(ns fm.websockets.samples.fileupload.file-upload-service
  (:require
    [clojure.contrib.logging :as log]
    [fm.websockets.app.boot :as boot]
    [fm.websockets.app.config :as cfg]
    [fm.websockets.rpc.request :as req]
    [fm.websockets.rpc.targets :as tar])
  (:import
    (java.io File FileOutputStream IOException)
    (java.util UUID)))

(tar/defroute (tar/prefixed-request-name-route "file-upload" "."))

(defn- prepare-uploads-directory []
  (let [directory (File. (System/getProperty "user.home") "Uploads")]
    (if (and (.isDirectory directory) (.canWrite directory))
      directory
      (if (or (.exists directory) (not (.mkdirs directory)))
        (throw (IOException. "Failed to create 'Uploads' directory!"))
        directory))))

(boot/def-boot-hook (fn [config]
                      (assoc config ::uploads-directory 
                                    (prepare-uploads-directory))))

(defn- data-bytes [data]
  (.getBytes (str data) "ISO-8859-1"))

(defn- create-upload-directory [id]
  (let [directory (-> (req/connection) cfg/get-config ::uploads-directory)
        directory (File. directory id)]
    (if (or (.exists directory) (not (.mkdirs directory)))
      (throw (IOException. "Failed to create upload directory!"))
      directory)))

(defn- make-resource [file-name]
  (let [id     (str (UUID/randomUUID))
        file   (File. (create-upload-directory id) file-name)
        output (FileOutputStream. file)]
    {::id id ::file file ::output output}))

(defn- start-upload [file-name & [data]]
  (log/debug (format "Started upload of file '%s'." file-name))
  (let [{output ::output :as resource} (make-resource file-name)]
    (when data
      (.write output (data-bytes data)))
    resource))

(defn- continue-upload [{output ::output file ::file} data]
  (log/debug (format "Uploading contents of file '%s'..." (.getName file)))
  (.write output (data-bytes data))
  nil)

(defn- finish-upload [{output ::output file ::file}]
  (log/debug (format "Upload of file '%s' finished." (.getName file)))
  (.close output)
  nil)

(defn- abort-upload [{output ::output file ::file}]
  (log/debug (format "Upload of file '%s' aborted!" (.getName file)))
  (.close output)
  (let [upload-directory (.getParentFile file)]
    (.delete file)
    (when upload-directory
      (.delete upload-directory))
    nil))

(tar/defchannel upload-file :open  start-upload
                            :write continue-upload
                            :abort abort-upload
                            :close finish-upload)

