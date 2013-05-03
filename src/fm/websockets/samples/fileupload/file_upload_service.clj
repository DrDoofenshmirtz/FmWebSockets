(ns fm.websockets.samples.fileupload.file-upload-service
  (:require
    [clojure.contrib.logging :as log]    
    [fm.websockets.rpc.targets :as tar])
  (:import
    (java.io File FileOutputStream IOException)
    (java.util UUID)))

(defn- data-bytes [data]
  (.getBytes (str data) "ISO-8859-1"))

(defn- create-upload-directory [id]
  (let [directory (File. (System/getProperty "user.home") "Uploads")
        directory (File. directory id)]
    (if (or (.exists directory) (not (.mkdirs directory)))
      (throw (IOException. "Failed to create upload directory!"))
      directory)))

(defn- make-resource [file-name]
  (let [id     (str (UUID/randomUUID))
        file   (File. (create-upload-directory id) file-name)
        output (FileOutputStream. file)]
    {::id id ::file file ::output output}))

(defn- start-upload [file-name data]
  (let [{output ::output :as resource} (make-resource file-name)]
    (.write output (data-bytes data))
    resource))

(defn- continue-upload [{output ::output} data]
  (.write output (data-bytes data))
  nil)

(defn- finish-upload [{output ::output}]
  (.close output)
  nil)

(defn- abort-upload [{output ::output file ::file}]
  (.close output)
  (let [upload-directory (.getParentFile file)]
    (.delete file)
    (when upload-directory
      (.delete upload-directory))
    nil))

(def upload-file (tar/channel {:open  start-upload
                               :write continue-upload
                               :abort abort-upload
                               :close finish-upload}))

