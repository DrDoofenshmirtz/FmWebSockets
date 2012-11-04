(ns fm.websockets.samples.fileupload.file-upload-service
  (:use
    [clojure.contrib.logging :only (debug)]
    [fm.core.bytes :only (signed-byte)]
    [fm.websockets.json-rpc :only (result)]
    [fm.websockets.resources :only (manage-resource
                                    get-resource
                                    remove-resources
                                    update-resources)]
    [fm.websockets.resources.storage :only (resource)])
  (:import
    (java.io File FileOutputStream)
    (java.util UUID)))

(defn- data-bytes [data]
  (into-array Byte/TYPE (map signed-byte data)))

(defn- on-event [id event resource]
  (debug (format "on-event{id: %s resource: %s}" id resource))
  (if (and (= id :scope-expired) (not= (:scope event) :request))
    (assoc resource :delete? true)
    resource))

(defn- expired? [resource]
  (debug (format "expired?{resource: %s}" resource))
  (:expired? resource))

(defn- close! [{:keys [file output delete?] :as resource}]
  (debug (format "close!{resource: %s}" resource))
  (.close output)
  (if delete?
    (.delete file)))

(defn- start-upload [connection upload]
  (let [{file-name :fileName data :data} upload
        file     (File. "/home/frank/Uploads" file-name)
        output   (FileOutputStream. file)
        id       (str (UUID/randomUUID))
        resource {:file file :output output}]
    (manage-resource connection
                     id resource
                     :connection
                     :on-event on-event
                     :expired? expired?
                     :close!   close!)
    (.write output (data-bytes data))
    id))

(defn- continue-upload [connection upload]
  (let [{:keys [id data]} upload
        {output :output :as resource} (get-resource connection id)]
    (if-not resource
      (throw (IllegalStateException.
               "Upload failed (resource has been closed)!")))
    (.write output (data-bytes data))
    id))

(defn- finish-upload [connection upload]
  (let [{id :id} upload]
    (remove-resources connection id)
    nil))

(defn- abort-upload [connection upload]
  (let [{id :id} upload]
    (update-resources connection
                      #(assoc % :expired? true :delete? true)
                      :keys [id])
    nil))

(defn upload-file [connection upload]
  (debug (format "upload-file{upload: %s}" upload))
  (let [{state :state} upload]
    (case state
      "STARTED"     (start-upload connection upload)
      "IN_PROGRESS" (continue-upload connection upload)
      "DONE"        (finish-upload connection upload)
      "ABORTED"     (abort-upload connection upload))))
