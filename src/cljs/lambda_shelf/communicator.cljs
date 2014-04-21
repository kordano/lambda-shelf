(ns lambda-shelf.communicator
  (:require [goog.net.XhrIo :as xhr]
            [goog.net.WebSocket]
            [goog.net.WebSocket.EventType :as event-type]
            [goog.events :as events]
            [cljs.reader :refer [read-string]]
            [cljs.core.async :as async :refer [<! >! chan close! put!]])
  (:require-macros [cljs.core.async.macros :refer [go alt! go-loop]]))


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

(defn connect!
  ([uri] (connect! uri {}))
  ([uri {:keys [in out] :or {in chan out chan}}]
      (let [on-connect (chan)
            in (in)
            out (out)
            websocket (goog.net.WebSocket.)]
        (.log js/console "establishing websocket ...")
        (doto websocket
          (events/listen event-type/MESSAGE
                         (fn [m]
                              (let [data (read-string (.-message m))]
                                (put! out data))))
          (events/listen event-type/OPENED
                         (fn []
                           (close! on-connect)
                           (.log js/console "channel opened")
                           (go-loop []
                                    (let [data (<! in)]
                                      (if-not (nil? data)
                                        (do (.send websocket (pr-str data))
                                            (recur))
                                        (do (close! out)
                                            (.close websocket)))))))
          (events/listen event-type/CLOSED
                         (fn []
                            (.log js/console "channel closed")
                            (close! in)
                            (close! out)))
          (events/listen event-type/ERROR (fn [e] (.log js/console (str "ERROR:" e))))
          (.open uri))
        (go
          (<! on-connect)
          {:uri uri :websocket websocket :in in :out out}))))

;; --- testing ---
#_(go
  (let [connection (<! (connect! "ws://localhost:8080/bookmark/ws"))]
    (>! (:in connection) {:topic :greeting :data ""})
    (.log js/console (str (<! (:out connection))))
    (>! (:in connection) {:topic :get-all :data ""})
    (.log js/console (str (count (<! (:out connection)))))
    (>! (:in connection) {:topic :fetch-title :data {:url "http://xkcd.com/378/"}})
    (.log js/console (str (<! (:out connection))))
    (.close (:websocket connection))))
