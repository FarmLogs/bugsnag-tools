# Bugsnag Tools

Tools for querying the Bugsnag API.

These are still in active development, API changes should be expected across
major versions.

## Usage

First, grab your account's auth token from bugsnag: https://bugsnag.com/accounts/farmlogs/edit

Since this only lists error events at the moment, you'll also need to grab your
error id from its URL in bugsnag.

    => (events auth-token error-id)

Since you might have many events for a given error, events will yield a lazy
sequence.  It's recommended take a small samples instead of using the entire
list.

    => (take 100 (events auth-token error-id))

For ease of use, chain your operations together with `->>`:

    => (->> (events auth-token error-id)
            (take 100)
            (map #(get-in % [:meta_data :request]))
            (group-by :uri))
