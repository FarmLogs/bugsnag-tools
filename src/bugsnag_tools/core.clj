(ns bugsnag-tools.core
  "Tools for analyzing bugsnag error data.

   This is an incomplete implementation of the Bugsnag API.  For more details on
   querying the API, see the Bugsnag docs: https://bugsnag.com/docs/api"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def ^{:private true} host "https://api.bugsnag.com")

(defn- hostify
  "Convenience function for prepending paths with the hostname"
  [path]
  (if (= (subs path 0 4) "http")
    path
    (str host path)))

(defn retry
  "Given a function, f, run it after delay-ms milliseconds have
  passed."
  [f delay-ms]
  (Thread/sleep delay-ms)
  (f))

(defn rate-limit-middleware
  "Respect rate-limit requirements sent by the server.

   When an HTTP 429 status code is received, sleep for the prescribed
   amount of time. The amount of time may be communicated in the
   \"Retry-After\" header.

   Note: https://httpstatuses.com/429"
  [client]
  (fn [req]
    (try
      (client req)
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (if (= (:status data) 429)
            (do
              (retry #(client req)
                     (-> (get-in data [:headers "Retry-After"] "10")
                         (Integer/parseInt)
                         (* 1000))))
            (throw e)))))))
(defn- fetch
  "Makes an authenticated get request against the bugsnag api."
  ([auth-token next-page] (fetch auth-token next-page {}))
  ([auth-token next-page options]
   (when next-page
     (let [params (merge {"auth_token" auth-token "per_page" 100} options)
           response (http/with-middleware (into http/*current-middleware*
                                                [rate-limit-middleware])
                      (http/get (hostify next-page)
                                {:query-params params}))]
       (lazy-cat (json/decode (:body response) true)
                 (fetch auth-token
                        (get-in response [:links :next :href])
                        ; Params already appended to next, don't preserve them
                        {}))))))

(defn accounts
  "Lists the accounts to which the auth-token has access.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"

   Usage:
   => (accounts auth-token error-id)"
  [auth-token]
  (fetch auth-token "/accounts"))

(defn projects
  "Lists the projects on the given account.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"
   account-id \"1a644306e9625e0ad0fd25fa\"

   Usage:
   => (accounts auth-token account-id)"
  [auth-token account-id]
  (fetch auth-token (format "/accounts/%s/projects" account-id)))

(defn errors
  "Returns a lazy sequence of all errors in the given project.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"
   project-id \"1a644306e9625e0ad0fd25fa\"

   Usage:
   => (errors auth-token project-id)
   => (->> (errors auth-token project-id) (take 10))"
  [auth-token project-id & {:keys [release_stages
                                   app_versions
                                   severity
                                   status
                                   sort
                                   direction
                                   per_page
                                   most_recent_event]
                            :as options}]
  (fetch auth-token (format "/projects/%s/errors" project-id) options))

(defn events
  "Returns a lazy sequence of all events for the given error.
   Beware, this makes a series of http requests against the API, so it may not
   be very performant for high numbers of events.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"
   error-id \"1a644306e9625e0ad0fd25fa\"

   Usage:
   => (events auth-token error-id)
   => (->> (events auth-token error-id) (take 100))->>"
  [auth-token error-id & {:keys [sort direction per_page start_time end_time]
                          :as options}]
  (fetch auth-token (format "/errors/%s/events" error-id) options))
