# Privacy policy

This privacy policy details the data that is collected when you interact with batect and its related services.

## Documentation site statistical information

This documentation site ([batect.dev](https://batect.dev)) collects anonymous statistical information using Google Analytics.

### What data is collected

Google Analytics collects information such as the following:

* the number of unique users that visit the site
* how often a user returns to the site
* the referrer (eg. if you clicked on a link on a Google search result page, the search term, or if you clicked on a link on a blog post, the URL of the blog post)
* the amount of time spent on each page
* the flow of pages a user visits in a single session (eg. 50% of users that view the home page go to the CLI reference page next)
* the general geographic location (country or city) of the user based on IP address
* the browser-reported language preference
* the type of device being used (eg. phone or computer)
* technical information such as the browser version and operating system version
* performance information such as page load times

Google Analytics expressly [forbids](https://support.google.com/analytics/answer/6366371?hl=en-GB&utm_id=ad) collecting
personally identifiable information.

The following Google Analytics features are _not_ enabled:

* [Remarketing](https://support.google.com/analytics/answer/2611268?hl=en-GB&utm_id=ad)
* [Advertising Features](https://support.google.com/analytics/answer/3450482?hl=en-GB&utm_id=ad), which includes information such as age, gender
  and interests
* [User-ID](https://support.google.com/analytics/answer/3123662?hl=en-GB&utm_id=ad), which tracks users across devices
* [Google Signals](https://support.google.com/analytics/answer/7532985?hl=en-GB&utm_id=ad), which provides similar features to the above features

### How and why the data is used

This data is used to understand how users use the documentation, identify areas for improvement, identify technical issues and
measure the impact of changes to the documentation.

Data collected from Google Analytics is not combined with data from other sources to attempt to identify users.

Only the primary maintainer of batect, [Charles Korn](https://github.com/charleskorn) has access to the Google Analytics console for this site.

### How to opt-out

Google provides a [browser add-on](https://tools.google.com/dlpage/gaoptout) that blocks Google Analytics on all sites.

There are also a number of third-party add-ons that can block Google Analytics on a per-site basis.

### How to request for your data to be deleted

Send a request to [privacy@batect.dev](mailto:privacy@batect.dev). Your data will be deleted using the Google Analytics
[User Deletion API](https://developers.google.com/analytics/devguides/config/userdeletion/v3) within 14 days.

## Documentation site hosting

This documentation site ([batect.dev](https://batect.dev)) is hosted using GitHub Pages and Cloudflare. Both of these services collect data
to enable them to operate their services, and their respective privacy policies apply to this data.

## In-app update notifications

batect checks for updated versions and displays a reminder to the user if a newer version is available.

It uses the public, unauthenticated GitHub API to check for new versions. It checks for updates at most once every 36 hours.

Running `./batect --upgrade` uses the same API in the same way to deliver new versions of batect.

### What data is collected and how it is used

No personally identifiable information or credentials are sent to GitHub as part of this process, and [GitHub's privacy policy](https://github.com/site/privacy)
applies to any data it collects.

### How to opt-out

Run batect with `--no-update-notification` to disable checking for new versions.

Note that running `./batect --upgrade` ignores `--no-update-notification` if it is set, and will always use the GitHub API to check for a new version.

## Third-party services

The batect project uses a number of third-party services such as GitHub for code hosting, Spectrum for community chat and Bintray for artifact hosting.

When you interact with these services, their respective privacy policy applies.
