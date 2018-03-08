(ns vermilionsands.frostflower-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [is deftest]]
            [duratom.core :as duratom]
            [duratom.utils :as utils]
            [vermilionsands.frostflower :as frostflower])
  (:import [java.io File]
           [java.util.concurrent CountDownLatch]))

(defn- slurp-data [path]
  (-> path slurp read-string))

(defn- default-durable [file & [init rw]]
  (duratom/duratom
    :sync-local-file
    :init (or init {:a 0})
    :rw (merge @#'duratom.core/default-file-rw rw)
    :file-path (.getAbsolutePath file)))

(defn- future-swap [^CountDownLatch start ^CountDownLatch done a f & args]
  (future
    (.await start)
    (apply swap! a f args)
    (.countDown done)))

(defmacro with-temp-file [binding & body]
  `(let [~binding (io/file (str "target/" (gensym "tmp-")))]
     (.createNewFile ^File ~binding)
     (try
       ~@body
       (finally
         (io/delete-file ~binding)))))

(deftest backend-should-persist-data-test
  (with-temp-file tmp-file
    (let [durable (default-durable tmp-file)]
      (is (= {:a 0} @durable))
      (is (= {:a 0} (slurp-data tmp-file)))

      (swap! durable assoc :a 2 :b 3)
      (is (= {:a 2 :b 3} @durable))
      (is (= {:a 2 :b 3} (slurp-data tmp-file))))))

(deftest backend-should-load-data-test
  (with-temp-file tmp-file
    (let [expected {:a 1 :b 2 :c 3}]
      (spit tmp-file (prn-str expected))
      (let [durable (default-durable tmp-file)]
        (is (= expected @durable))
        (is (= expected (slurp-data tmp-file)))))))

(deftest subsequent-atom-init-test
  (with-temp-file tmp-file
    (let [data (into [] (range 100000))
          expected (conj data 0)]
      (doto (default-durable tmp-file [])
        (reset! data)
        (swap! conj 0))
      (let [durable (default-durable tmp-file)]
        (is (= expected @durable))))))

(deftest test-under-load
  (with-temp-file tmp-file
    (let [n 1000
          start (CountDownLatch. 1)
          done (CountDownLatch. n)
          durable (default-durable tmp-file [])]
      (doseq [x (range n)]
        (future-swap start done durable conj x))
      (.countDown start)
      (.await done)
      (is (= (set (range n))
             (set @durable)))
      (is (= (set (range n))
             (set (slurp-data tmp-file)))))))

(defn- slow-write [filepath data]
  (Thread/sleep (rand-int 100))
  (utils/write-edn-to-file! filepath data))

;; something similar to this test
;; failed for me on a Windows box, but I'm not able to reproduce it
;; on a linux, strange...
(deftest test-with-slow-serializier
  (with-temp-file tmp-file
    (let [n 100
          start (CountDownLatch. 1)
          done (CountDownLatch. n)
          durable (default-durable tmp-file {} {:write slow-write})
          expected (reduce #(assoc %1 %2 true) {} (range n))]
      (doseq [x (range n)]
        (future-swap start done durable assoc x true))
      (.countDown start)
      (.await done)
      (is (= expected @durable))
      (is (= expected (slurp-data tmp-file))))))