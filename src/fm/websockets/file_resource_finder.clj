(ns
  ^{:doc 
  
  "A resource finder that maps resource requests to local files."
  
    :author "Frank Mosebach"}
  fm.websockets.file-resource-finder
  (:require
    [clojure.contrib.logging :as log]
    [fm.core.io :as io])
  (:import
    (java.io FileInputStream ByteArrayOutputStream)))

(def ^:private ^:const extension->content-type {"css"  "text/css"
                                                "js"   "text/javascript"
                                                "jpeg" "image/jpeg"
                                                "gif"  "image/gif"
                                                "png"  "image/png"})

(defn- resource-path [http-exchange context-path]
  (let [resource-path (-> http-exchange .getHttpContext .getPath str)]
    (when (.startsWith resource-path context-path)
      (.substring resource-path (.length context-path)))))

(defn- extension [resource-path]
  (let [path-length (.length resource-path)]
    (when (> path-length 2)
      (let [dot-index (.lastIndexOf resource-path ".")]
        (when (and (pos? dot-index) (< dot-index (dec path-length)))
          (.substring resource-path (inc dot-index)))))))

(defn- content-type [resource-path]
  (if (.isEmpty resource-path)
    :application
    (extension->content-type extension)))

(defn- success [response]
  (assoc response :status HttpURLConnection/HTTP_OK))

(defn- read-resource [path]
  (with-open [input  (FileInputStream. path) 
              output (ByteArrayOutputStream.)]
    (doseq [chunk (io/byte-array-seq input)]
      (.write output chunk))
    (.toByteArray output)))



(defn finder [root-path app-path]
  (fn [request]
    (let [{:keys [app-name request-uri]} request
          resource-path (if (= :application content-type)
                          app-path
                          (content-type->route content-type))
          resource-path (if resource-path
                          (str root-path resource-path))]
      (when resource-path
        (read-resource resource-path)))))

