(ns lambda-shelf.client
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :refer [blank?]]
            [lambda-shelf.communicator :refer [post-edn get-edn]])
  (:require-macros [hiccups.core :as hiccups]
                   [cljs.core.async.macros :refer [go]]))


(.log js/console "HAIL TO THE LAMBDA!")

;; fire up repl
#_(do
    (def repl-env (reset! cemerick.austin.repls/browser-repl-env
                         (cemerick.austin/repl-env)))
    (cemerick.austin.repls/cljs-repl repl-env))


(def app-state (atom {:bookmarks nil
                      :notifications [""]}))


(defn handle-url-change [e owner {:keys [url-text]}]
  (om/set-state! owner :url-text (.. e -target -value)))


(defn handle-title-change [e owner {:keys [title-text]}]
  (om/set-state! owner :title-text (.. e -target -value)))


(defn apply-to-entry [x k f]
  "Apply function f to key k in entry x"
  (fn [xs]
    (vec
     (map #(if (= x %) (assoc % k (f (k %))) %) xs))))


(defn add-bookmark [app owner]
  "read input data, send it to server and update dom"
  (let [new-url (.-value (om/get-node owner "new-url"))
        package (str {:url new-url})]
    (go
      (>! (om/get-state owner :incoming) (<! (post-edn "bookmark/add" package)))
      (om/set-state! owner :url-text "")
      (om/set-state! owner :title-text ""))))


(defn bookmark-view [{:keys [title url date id votes] :as bookmark} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [incoming]}]
      (dom/tr nil
              (dom/td nil
                      (dom/div
                       #js {:onClick #(go
                                        (>! incoming
                                          (<! (post-edn "bookmark/vote" (str {:id id :upvote true})))))
                            :className "arrow up"})

                      (dom/br nil)

                      (dom/div
                       #js {:onClick #(go
                                        (>! incoming
                                          (<! (post-edn "bookmark/vote" (str {:id id :upvote false})))))
                             :className "arrow down"}))

              (dom/td #js {:className "bookmark-voting"} (dom/a nil votes))
              (dom/td #js {:className "bookmark-title"} (dom/a #js {:href url} title))
              (dom/td #js {:className "bookmark-date"} (dom/a nil (.toLocaleString date)))))))


(defn update-notification [app owner value]
  (let [notify-node (om/get-node owner "center-notification")]
    (go
      (om/transact! app :notifications (fn [xs] (conj xs value)))
      (set! (.-visibility (.-style notify-node)) "visible")
      (set! (.-opacity (.-style notify-node)) "1.0")
      (<! (timeout 2000))
      (set! (.-opacity (.-style notify-node)) "0.0")
      (<! (timeout 500))
      (set! (.-visibility (.-style notify-node)) "hidden"))))


(defn bookmarks-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:incoming (chan)
       :notify (chan)
       :url-text ""
       :shown 8
       :counter 0})


    om/IWillMount
    (will-mount [_]
      (let [incoming (om/get-state owner :incoming)
            notify (om/get-state owner :notify)
            counter (om/get-state owner :counter)]
        (go
          (loop []
            (let [[v c] (alts! [incoming notify])]
              (condp = c
                notify (update-notification app owner v)
                incoming (do
                           (om/transact! app :bookmarks (fn [_] (vec (sort-by :date > v))))
                           (om/set-state! owner :counter (count v))))
              (recur))))

        ;; welcome message
        (go
          (put! notify "Welcome to the shelf!"))

        ;; auto update bookmarks all 5 minutes
        (go
          (while true
            (.log js/console "Updating bookmarks ...")
            (>! incoming (<! (get-edn "bookmark/init")))
            (<! (timeout 300000))))))


    om/IRenderState
    (render-state [this {:keys [incoming shown notify counter url-text] :as state}]
      (dom/div
       nil

       ;; general notification container
       (dom/div
        #js {:id "main-notification" :className "notification-container"}
        (dom/span
         nil
         (dom/a
          #js {:id "testme"
               :ref "center-notification"}
          (last (:notifications app)))))

       ;; main container
       (dom/div
        #js {:id "bookmark-container" :className "container"}

        ;; container input
        (dom/div
         #js {:className "container-input"}

         (dom/span
          nil
          (dom/input
           #js {:type "text"
                :ref "new-url"
                :value url-text
                :placeholder "URL"
                :onChange #(handle-url-change % owner state)
                :onKeyPress #(when (== (.-keyCode %) 13)
                               (if (not (blank? url-text))
                                 (do
                                   (put! notify "Adding bookmark")
                                   (add-bookmark app owner))
                                 (put! notify "url input missing")))}))

         (dom/button
          #js {:onClick #(when (not (blank? url-text))
                           (do
                             (put! notify "bookmark added")
                             (add-bookmark app owner))
                           (put! notify "input missing"))
               :className "add-button"}
          "ADD"))

        ;; container header
        (dom/div
         #js {:className "container-header"}
         (dom/a nil "Bookmarks"))

        ;; container list
        (dom/div
         #js {:className "container-list"}
         (apply
          dom/table
          nil
          (om/build-all
           bookmark-view (take shown (:bookmarks app))
           {:init-state {:incoming incoming :notify notify}}))

         ;; control viewed amount of records with two buttons
         (dom/div
          #js {:className "paging-bar"}

          (dom/button
           #js {:onClick #(om/set-state!
                           owner
                           :shown
                           (if (< shown counter)
                             (+ shown 8)
                             shown))
                :className "nav-button"}
           "Show more")

          (dom/button
           #js {:onClick #(om/set-state!
                           owner
                           :shown
                           (if (> shown 8)
                             (- shown 8)
                             8))
                :className "nav-button"}
           "Show less"))))))))


(om/root
  bookmarks-view
  app-state
  {:target (. js/document (getElementById "bookmarks"))})
