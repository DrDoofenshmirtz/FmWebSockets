(ns fm.websockets.samples.fileupload.file-upload-service
  (:use
    [clojure.contrib.logging :only (debug)]
    [fm.core.bytes :only (signed-byte)]
    [fm.resources.core :only (expired)]
    [fm.websockets.json-rpc :only (result)]
    [fm.websockets.resources :only (store! get-resource send-to! remove!)])
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
  (debug (format "close!{resource: %s}" resource))
  (close! resource))

(def ^{:private true} slots {::done (fn [resource]
                                      (-> resource
                                          (assoc ::close! save)
                                          expired))})

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

(defn- start-upload [connection upload]
  (let [{file-name :fileName data :data} upload
        id (str (UUID/randomUUID))
        {output ::output :as resource} (make-resource id file-name)]
    (store! connection id resource :connection :close! close! :slots slots)
    (.write output (data-bytes data))
    id))

(defn- continue-upload [connection upload]
  (let [{:keys [id data]} upload
        {output ::output :as resource} (get-resource connection id)]
    (if-not resource
      (throw (IllegalStateException.
               "Upload failed (file has been closed)!")))
    (.write output (data-bytes data))
    id))

(defn- finish-upload [connection upload]
  (let [{id :id} upload]
    (send-to! connection [id] ::done)
    nil))

(defn- abort-upload [connection upload]
  (let [{id :id} upload]
    (remove! connection id)
    nil))

(defn upload-file [connection upload]
  (debug (format "upload-file{upload: %s}" upload))
  (let [{state :state} upload]
    (case state
      "STARTED"     (start-upload connection upload)
      "IN_PROGRESS" (continue-upload connection upload)
      "DONE"        (finish-upload connection upload)
      "ABORTED"     (abort-upload connection upload))))
