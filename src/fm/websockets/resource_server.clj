(ns
  ^{:doc 
  
  "A basic http server tailored to deliver the resources of a WebSockets app."
  
    :author "Frank Mosebach"}
  fm.websockets.resource-server
  (:require
    [clojure.contrib.logging :as log])
  (:import
    (java.util.regex Pattern)
    (java.net InetSocketAddress HttpURLConnection)
    (com.sun.net.httpserver HttpServer HttpHandler)))

(defn- context-path [app-name]
  (let [app-name (.trim (str app-name))]
    (if (.isEmpty app-name)
      (throw (IllegalArgumentException. "The app name must not be empty!"))
      (str "/" app-name))))

(defn- resource-path [request-path app-name]
  (let [[_ head tail] (-> (str "^/*(" (Pattern/quote app-name) ")/*(/.*)") 
                          re-pattern 
                          (re-matches request-path))]
    (when (and (= app-name head) tail)
      tail)))

(defn- resource-request [request-path app-name]
  (when-let [resource-path (resource-path request-path app-name)]
    {:app-name      app-name
     :resource-path resource-path}))

(defn- failure [request-path]
  (let [message (format "Request for resource '%s' failed!" request-path)]
    {:status       HttpURLConnection/HTTP_UNAVAILABLE
     :content-type "text/plain"
     :content      (.getBytes message "UTF-8")}))

(defn- send-response [http-exchange response]
  (let [{:keys [status content-type content]} response]
    (-> http-exchange 
        (.sendResponseHeaders status (alength content)))
    (-> http-exchange 
        .getResponseHeaders 
        (.set "Content-Type" content-type))
    (-> http-exchange
        .getResponseBody
        (.write content))))

(defn- request-handler [resource-finder app-name]
  (fn [http-exchange]
    (let [request-path (-> http-exchange .getRequestURI .getPath)]
      (log/debug (format "Handling request for resource: %s." request-path))
      (if-let [request (resource-request request-path app-name)]
        (if-let [response (resource-finder request)]
          (send-response http-exchange response)
          (send-response http-exchange (failure request-path)))
        (send-response http-exchange (failure request-path))))))

(defn- http-handler [resource-finder app-name]
  (let [request-handler (request-handler resource-finder app-name)]
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

(defn start-up [port app-name resource-finder]
  (log/debug "Starting resource server...")
  (if (nil? port)
    (throw (IllegalArgumentException. "The port must be a valid number!")))
  (if (nil? resource-finder)
    (throw (IllegalArgumentException. "The resource finder must not be nil!")))
  (let [context-path (context-path app-name)
        http-handler (http-handler resource-finder app-name)
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

