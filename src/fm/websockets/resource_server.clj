(ns
  ^{:doc 
  
  "A basic HTTP Server tailored to host the Resources of a Single Page App."
  
    :author "Frank Mosebach"}
  fm.websockets.resource-server
  (:require
    [clojure.contrib.logging :as log])
  (:import
    (java.net InetSocketAddress HttpURLConnection)
    (com.sun.net.httpserver HttpServer HttpHandler HttpExchange)))

(def ^:private ^:const extension->content-type {"js"   ["text"  "javascript"]
                                                "jpeg" ["image" "jpeg"]
                                                "gif"  ["image" "gif"]
                                                "png"  ["image" "png"]})

(defn- context-path [app-name]
  (let [app-name (.trim (str app-name))]
    (if (.isEmpty app-name)
      (throw (IllegalArgumentException. "The app name must not be empty!"))
      (str "/" app-name))))

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

(defn- resource-request [http-exchange resource-path]
  (let [content-type  (content-type resource-path)]    
    (when content-type
      (assoc (if (.isEmpty resource-path) 
               {} 
               {:resource-path resource-path}) 
             :content-type content-type))))

(defn- send-response [http-exchange response]
  (println response))

(defn- request-handler [resource-router context-path]
  (fn [http-exchange]
    (if-let [path (resource-path http-exchange context-path)]
      (if-let [request (resource-request http-exchange path)]
        (if-let [resource (resource-router request)]
          (send-response http-exchange 
                         (assoc request :status   HttpURLConnection/HTTP_OK
                                        :resource resource))
          (send-response http-exchange
                         {:status       HttpURLConnection/HTTP_NOT_FOUND
                          :content-type ["text" "plain"]
                          :resource     "Resource not found!"}))
        (send-response http-exchange
                       {:status       HttpURLConnection/HTTP_UNSUPPORTED_TYPE
                        :content-type ["text" "plain"]
                        :resource     "Unsupported resource type!"}))
      (send-response http-exchange
                     {:status       HttpURLConnection/HTTP_FORBIDDEN
                      :content-type ["text" "plain"]
                      :resource     "Invalid resource path!"}))))

(defn- http-handler [resource-router context-path]
  (let [request-handler (request-handler resource-router context-path)]
    (reify
      HttpHandler
      (handle [this http-exchange]
        (log/debug "Handle resource request...")
        (try
          (request-handler http-exchange)
          (log/debug "Successfully handled resource request.")
          (catch Throwable error
            (log/error "Failed to handle resource request!" error))
          (finally 
            (.close http-exchange)))))))

(defn start-up [port app-name resource-router]
  (log/debug "Starting resource server...")
  (if (nil? port)
    (throw (IllegalArgumentException. "The port must be a valid number!")))
  (if (nil? resource-router)
    (throw (IllegalArgumentException. "The resource router must not be nil!")))
  (let [context-path (context-path app-name)
        http-handler (http-handler resource-router context-path)
        http-server  (doto (HttpServer/create (InetSocketAddress. port) 10)
                           (.createContext context-path http-handler)
                           (.start))]
    (log/debug (format "Resource server is running (port: %, path: %s)." 
                       port context-path))    
    (fn []
      (log/debug (format "Stopping resource server (port: %, path: %s)..." 
                         port context-path))
      (.stop http-server 0)
      (log/debug "Resource server has been stopped."))))

