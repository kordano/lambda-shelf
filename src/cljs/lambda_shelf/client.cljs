(ns lambda-shelf.client
  (:require [hiccups.runtime :as hiccupsrt]
            [clojure.browser.repl]
            [cljs.core.async :refer [put! chan <! >! alts! timeout close!]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [blank?]]
            [sablono.core :as html :refer-macros [html]]
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


(defn handle-text-change [e owner {:keys [text]} type]
  (om/set-state! owner [:input-text type] (.. e -target -value)))


(defn apply-to-entry [x k f]
  "Apply function f to key k in entry x"
  (fn [xs]
    (vec
     (map #(if (= x %) (assoc % k (f (k %))) %) xs))))


(defn add-bookmark [app owner]
  "read input data, send it to server and update dom"
  (let [new-url (.-value (om/get-node owner "new-url"))
        new-title (.-value (om/get-node owner "new-title"))
        package (str {:url new-url :title new-title})]
    (go
      (>! (om/get-state owner :incoming) (<! (post-edn "bookmark/add" package)))
      (om/set-state! owner [:input-text :url] "")
      (om/set-state! owner [:input-text :title] ""))))


(defn fetch-url-title [app owner url]
  (let [package (str {:url url})]
    (go
      (let [title (:title (<! (post-edn "bookmark/fetch-title" package)))]
        (.log js/console title)
        (om/set-state! owner [:input-text :title] title)))))


(defn bookmark-view [{:keys [title url date id votes] :as bookmark} owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [incoming]}]
      (html
       [:tr
        [:td.bookmark-voting [:a votes]]
        [:td.bookmark-title [:a {:href url} title]]
        [:td.bookmark-date [:a (.toLocaleString date)]]
        [:td [:button.btn.btn-default.btn-xs
              {:type "button"
               :on-click #(go
                            (>! incoming
                              (<! (post-edn "bookmark/vote" (str {:id id :upvote true})))))}
              [:span.glyphicon.glyphicon-plus]]]]))))

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
       :fetch (chan)
       :notify (chan)
       :input-text {:url "" :title ""}
       :page 0
       :page-size 8
       :counter 0})

    om/IWillMount
    (will-mount [_]
      (let [incoming (om/get-state owner :incoming)
            notify (om/get-state owner :notify)
            counter (om/get-state owner :counter)
            fetch (om/get-state owner :fetch)]
        (go
          (loop []
            (let [[v c] (alts! [incoming notify fetch])]
              (condp = c
                notify (update-notification app owner v)
                incoming (do
                           (om/transact! app :bookmarks (fn [_] (vec (sort-by :date > v))))
                           (om/set-state! owner :counter (count v)))
                fetch (fetch-url-title app owner v))
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
    (render-state [this {:keys [incoming page page-size notify counter input-text fetch] :as state}]
      (html
       [:div
        ;; general notification container
        [:div#main-notification.notifcation-container
         [:span
          [:a {:ref "center-notification"} (last (:notifications app))]]]


        ;; container input
        [:div#input-form {:role "form"}
         [:div.form-group
          [:label {:for "bookmark-url-input"} "bookmark url"]
         [:input#bookmark-url-input.form-control
          {:type "url"
           :ref "new-url"
           :value (:url input-text)
           :placeholder "URL"
           :on-change #(handle-text-change % owner state :url)
           :onKeyPress #(when (== (.-keyCode %) 13)
                          (if (not (blank? (:url input-text)))
                            (do
                              (put! notify "Adding bookmark")
                              (add-bookmark app owner))
                            (put! notify "url input missing")))}]]

         [:label {:for "bookmark-title-input"} "Bookmark Title"]
         [:div.input-group
          [:input#bookmark-title-input.form-control
           {:type "text"
            :ref "new-title"
            :value (:title input-text)
            :placeholder "Title"
            :on-change #(handle-text-change % owner state :title)
            :onKeyPress #(when (== (.-keyCode %) 13)
                           (if (not (blank? (:url input-text)))
                             (do
                               (put! notify "Adding bookmark")
                               (add-bookmark app owner))
                             (put! notify "input missing")))}]
          [:span.input-group-btn
           [:button.btn.btn-default {:type "button"
                                     :on-click #(if (not (blank? (:url input-text)))
                                                  (do
                                                    (put! notify "fetching title ...")
                                                    (put! fetch (:url input-text)))
                                                  (put! notify "input missing"))}
            "Fetch!"]]]]

        [:br]
        [:button.btn.btn-primary
         {:on-click #(if (not (blank? (:url input-text)))
                       (do
                         (put! notify "bookmark added")
                         (add-bookmark app owner))
                       (put! notify "input missing"))
          :type "button"}
         "ADD"]

        ;; container header

        [:h2.page-header "Bookmarks"]

        ;; container list

        [:div.table-responsive
         [:table.table.table-striped
          [:tbody
           (om/build-all bookmark-view (take page-size (drop (* page-size page) (:bookmarks app)))
                         {:init-state {:incoming incoming :notify notify}})]]]

        [:ul.pager
         [:li.previous
          [:a#prev-page-btn
           {:href "#"
            :on-click #(do
                         (.log js/console page)
                         (om/set-state!
                          owner
                          :page
                          (if (> page 0)
                            (dec page)
                            0)))}
           "newer"]]
         [:li.next
          {:on-click #(let [not-end? (< (* page-size (inc page)) counter)]
                        (om/set-state!
                         owner
                         :page
                         (if not-end?
                           (inc page)
                           page)))}
          [:a {:href "#"} "older"]]]]))))


(om/root bookmarks-view app-state {:target (. js/document (getElementById "bookmarks"))})
