(ns frostflower
  (:require [clojure.java.io :as io]
            [duratom.backends :as backends]
            [duratom.core :as duratom]
            [duratom.utils :as ut])
  (:import [java.io File]
           [java.nio.file AccessDeniedException]
           [java.util.concurrent.locks ReentrantLock]))

(def ^:private default-file-rw @#'duratom/default-file-rw)
(def ^:private map->Duratom @#'duratom/map->Duratom)

(defn- save! [path write-fn state-atom]
  (let [tmp-file-name (str path ".tmp")]
    (write-fn tmp-file-name @state-atom)
    (try
      (ut/move-file! tmp-file-name path)
      ;; retry for occasional Windows error
      (catch AccessDeniedException _
        (Thread/sleep 10)
        (ut/move-file! tmp-file-name path)))))

(defn- read! [path read-fn]
  (try
    [(read-fn path) nil]
    (catch Exception e
      [nil e])))

(deftype SyncFileBackend [^File file read-fn write-fn parent-atom]
  backends/IStorageBackend
  (snapshot [_]
    (let [path (.getAbsolutePath file)]
      (cond
        (nil? file)
        (throw (IllegalArgumentException. "No file to store data!"))

        (zero? (.length file))
        nil

        :else
        (let [[x error] (read! path read-fn)]
          (if-not error
            x
            (throw (IllegalStateException.
                     (str "Unable to read data from files " path "!"))))))))

  (commit [_]
    (-> (.getAbsolutePath file)
        (save! write-fn parent-atom)))

  (cleanup [_]
    (io/delete-file file)))

(defn- map->SyncFileBackend [{:keys [file read-fn write-fn parent-atom]}]
  (->SyncFileBackend file read-fn write-fn parent-atom))

(defn sync-file-atom
  [file-path lock initial-value rw]
  (let [file (io/file file-path)]
    (.createNewFile file)
    (map->Duratom
      {:lock lock
       :init initial-value
       :make-backend (fn [parent-agent]
                       (map->SyncFileBackend
                         {:file        file
                          :read-fn     (:read rw)
                          :write-fn    (:write rw)
                          :parent-atom @parent-agent}))})))

(defmethod duratom/duratom :sync-local-file
  [_ & {:keys [file-path init lock rw]
        :or   {lock (ReentrantLock.)
               rw   default-file-rw}}]
  (sync-file-atom file-path lock init rw))