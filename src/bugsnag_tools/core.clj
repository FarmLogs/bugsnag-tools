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

(defn- fetch
  "Makes an authenticated get request against the bugsnag api."
  [auth-token uri]
  (http/get (hostify uri) {:query-params {"auth_token" auth-token}}))

(defn events
  "Returns a lazy sequence of all events for the given error.
   Beware, this makes a series of http requests against the API, so it may not
   be very performant for high numbers of events.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"
   error-id \"1a644306e9625e0ad0fd25fa\"

   Usage:
   => (events auth-token error-id)
   => (->> (events auth-token error-id) (take 100))"
  ([auth-token error-id]
   (events auth-token error-id (format "/errors/%s/events?per_page=100" error-id)))
  ([auth-token error-id next-page]
   (let [response (fetch auth-token next-page)]
     (lazy-cat (json/decode (:body response) true)
               (events auth-token error-id (get-in response [:links :next :href]))))))
