(ns fm.websockets.samples.fileupload.file-upload-service
  (:require
    [clojure.contrib.logging :as log]
    [fm.resources.core :as rsc]
    [fm.websockets.resources :as wsr]
    [fm.websockets.rpc.request :as req])
  (:import
    (java.io File FileOutputStream IOException)
    (java.util UUID)))

(defn- data-bytes [data]
  (.getBytes (str data) "ISO-8859-1"))

(defn- save [{output ::output}]
  (.close output))

(defn- delete [{output ::output file ::file}]
  (.close output)
  (let [upload-directory (.getParentFile file)]
    (.delete file)
    (if upload-directory
      (.delete upload-directory))))

(defn- close! [{close! ::close! :as resource}]
  (log/debug (format "close!{resource: %s}" resource))
  (close! resource))

(def ^{:private true} slots {::done (fn [resource]
                                      (-> resource
                                          (assoc ::close! save)
                                          rsc/expired))})

(defn- create-upload-directory [id]
  (let [directory (File. (System/getProperty "user.home") "Uploads")
        directory (File. directory id)]
    (if (or (.exists directory) (not (.mkdirs directory)))
      (throw (IOException. "Failed to create upload directory!"))
      directory)))

(defn- make-resource [id file-name]
  (let [file   (File. (create-upload-directory id) file-name)
        output (FileOutputStream. file)]
    {::file file ::output output ::close! delete}))

(defn- start-upload [upload]
  (let [{file-name :fileName data :data} upload
        id (str (UUID/randomUUID))
        {output ::output :as resource} (make-resource id file-name)]
    (wsr/store! (req/connection) id resource 
                :connection 
                :close! close! 
                :slots  slots)
    (.write output (data-bytes data))
    id))

(defn- continue-upload [upload]
  (let [{:keys [id data]} upload
        {output ::output :as resource} (wsr/get-resource (req/connection) id)]
    (if-not resource
      (throw (IllegalStateException.
               "Upload failed (file has been closed)!")))
    (.write output (data-bytes data))
    id))

(defn- finish-upload [upload]
  (let [{id :id} upload]
    (wsr/send-to! (req/connection) [id] ::done)
    nil))

(defn- abort-upload [upload]
  (let [{id :id} upload]
    (wsr/remove! (req/connection) id)
    nil))

(defn upload-file [upload]  
  (let [{:keys [id state]} upload]
    (log/debug (format "upload-file{%s %s}" id state))
    (case state
      "STARTED"     (start-upload upload)
      "IN_PROGRESS" (continue-upload upload)
      "DONE"        (finish-upload upload)
      "ABORTED"     (abort-upload upload))))

