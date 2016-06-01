# watchboard
=============
Simple dashboard for Amazon Cloudwatch graphs.

Intro
-----
Backend: Uses Selenium Webdriver and PhantomJS to log in to the AWS console, retrieve the configured CloudWatch graphs, makes screenshots, crops and saves to disk.
Frontend: Auto-updating frontend using jQuery that fetches status and images from the backend, built with Jetty.

Usage
-----
Build project using maven.
Setup config.json (see config.example.json for an example).
Make sure phantomjs2 is installed (through npm, for example) and is available in the path as 'phantomjs'.
Put the jar and config in the same dir, run the jar.
Open the web interface on the port you configured in config.json.

Updaten naar laatste snapshot op watchboard machine
-----
1) Ga naar de watchboard instance
2) Sudo naar root
3) /opt/sw/watchboard/deploy.sh
