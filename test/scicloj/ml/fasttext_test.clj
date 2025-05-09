(ns scicloj.ml.fasttext-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :as t :refer [deftest is]]
   [scicloj.metamorph.ml :as ml]
   [tech.v3.dataset.metamorph :as dsmm]
   [scicloj.metamorph.core :as mm]
   [scicloj.metamorph.ml.loss :as loss]
   [scicloj.ml.fasttext]
   [tech.v3.dataset :as ds]
   [tech.v3.dataset.categorical :as ds-cat]
   [tech.v3.dataset.modelling]))

(defn delete-files-recursively
  [f1 & [silently]]
  (when (.isDirectory (io/file f1))
    (doseq [f2 (.listFiles (io/file f1))]
      (delete-files-recursively f2 silently)))
  (io/delete-file f1 silently))

(def primary
  (->
   (ds/->dataset "test/data/primary_small.nippy" {:key-fn keyword})))

(deftest fasttext-train

  (let [model
        (ml/train (-> primary
                      (tech.v3.dataset/categorical->number [:is.primary] [] :int)
                      (tech.v3.dataset.modelling/set-inference-target :is.primary))
                  {:model-type :djl/fasttext})
        prob-distribution
        (ml/predict (ds/head primary) (assoc model
                                             :top-k 3))]
    (delete-files-recursively (io/file (-> model :model-data :model-dir)))
    (is (= [0.0 0.0 0.0 0.0 0.0] (prob-distribution :is.primary)))))


(deftest fit-transform

  (let [pipe (mm/pipeline
              (dsmm/set-inference-target :is.primary)
              (dsmm/categorical->number [:is.primary] [] :int)
              {:metamorph/id :model}
              (ml/model {:model-type :djl/fasttext}))

        fit-ctx
        (mm/fit primary pipe)

        transform-result
        (mm/transform-pipe primary pipe fit-ctx)]

    

    (is (some?
         (-> fit-ctx :model :model-data :model-file)))

    (is (some?
         (-> fit-ctx :model :model-data :model-dir)))

    (is (= {"yes" 1, "no" 0, "unclear" 2}
           (-> fit-ctx :model :target-categorical-maps :is.primary :lookup-table)))

    (is (= 0.0
           (-> transform-result :metamorph/data :is.primary first)))))

(deftest fasttext-pipe
  (let [ pipe
        (mm/pipeline
         (dsmm/set-inference-target :is.primary)
         (dsmm/categorical->number [:is.primary] [] :int)
         {:metamorph/id :model}
         (ml/model {:model-type :djl/fasttext}))

        result
        (ml/evaluate-pipelines [pipe]
                               [{:train primary :test primary}]
                               loss/classification-accuracy
                               :accuracy
                               {:evaluation-handler-fn identity})]
    (is (= {"yes" 100}
           (-> result first first :test-transform :ctx :metamorph/data (tech.v3.dataset.categorical/reverse-map-categorical-xforms) :is.primary frequencies)))))


(deftest train-predict
  (let [pipe
        (mm/pipeline
         (dsmm/set-inference-target :label)
         (dsmm/categorical->number [:label] [] :int)
         {:metamorph/id :model}
         (ml/model {:model-type :djl/fasttext}))

        data (ds/->dataset {:text ["green" "red" "yellow"]
                            :label ["green" "red" "yellow"]})
        fit-ctx
        (mm/fit data pipe)

        transform-result
        (mm/transform-pipe data pipe fit-ctx)]

    (is (= ["green" "red" "yellow"]
           (:label (ds-cat/reverse-map-categorical-xforms (:metamorph/data transform-result)))))))
