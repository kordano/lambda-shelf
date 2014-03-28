(ns lambda-shelf.communicator
  (:require [goog.net.XhrIo :as xhr]
            [cljs.reader :refer [read-string]]
            [cljs.core.async :as async :refer [chan close! put!]])
  (:require-macros [cljs.core.async.macros :refer [go alt!]]))

(def websocket* (atom nil))

(defn GET [url]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (put! ch (-> event .-target .getResponseText))
                (close! ch)))
    ch))


(defn POST [url payload]
  (let [ch (chan 1)]
    (xhr/send url
              (fn [event]
                (put! ch (-> event .-target .getResponseText))
                (close! ch))
              "POST"
              payload)
    ch))


(defn get-edn [url]
  (go
   (-> (GET url) <! read-string)))


(defn post-edn [url payload]
  (go
   (-> (POST url (.toString payload))
       <!
       read-string)))


;; --- WEBSOCKET CONNECTION ---

(defn send! [data]
  (.send @websocket* (str data)))


(defn- take-all! [raw-message]
  (let [message (read-string raw-message)]
    (.log js/console (str "data received: " message))))


(defn client-connect! []
  (.log js/console "establishing websocket ...")
  (reset! websocket* (js/WebSocket. "ws://localhost:8080/bookmark/ws"))
  (doall
   (map #(aset @websocket* (first %) (second %))
        [["onopen" (fn [] (do
                           (.log js/console "channel opened")
                           (.send @websocket* {:topic :greeting :data []})))]
         ["onclose" (fn [] (.log js/console "channel closed"))]
         ["onerror" (fn [e] (.log js/console (str "ERROR:" e)))]
         ["onmessage" (fn [m]
                        (let [data (.-data m)]
                          (take-all! data)))]]))
  (.log js/console "websocket loaded."))
