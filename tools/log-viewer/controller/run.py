#! /usr/bin/env python3

import datetime
import json
import os
import sys
import urllib.parse

import requests


def main():
    logDetails = loadLogData()
    kibanaDetails = configureKibana()
    url = generateViewerUrl(logDetails, kibanaDetails)

    print("Ready to go - logs are available at {}".format(url))
    waitForExit()


def loadLogData():
    print("Loading log data...")

    requestBody = ""
    id = 1
    timestamps = []

    with open("/data/log.json") as jsonFile:
        for line in jsonFile:
            requestBody += json.dumps({
                "index": {
                    "_index": "logs",
                    "_type": "doc",
                    "_id": id
                }
            })

            requestBody += "\n"

            parsedLine = json.loads(line)
            parsedLine["logLineNumber"] = id

            requestBody += json.dumps(parsedLine) + "\n"

            id += 1

            timestamps.append(parsedLine["@timestamp"])

    response = requests.post(
        getElasticsearchUrl("/_bulk?refresh=true"),
        headers={"Content-Type": "application/x-ndjson"},
        data=requestBody
    )

    response.raise_for_status()

    responseBody = response.json()

    if responseBody["errors"] == True:
        print("Log upload failed.")
        print(response.text)
        sys.exit(-1)

    timestamps = list(map(parseTimestamp, timestamps))

    return {
        "minTimestamp": min(timestamps),
        "maxTimestamp": max(timestamps)
    }


def parseTimestamp(value):
    try:
        return datetime.datetime.strptime(value, '%Y-%m-%dT%H:%M:%S.%fZ').astimezone(datetime.timezone.utc)
    except ValueError:
        return datetime.datetime.strptime(value, '%Y-%m-%dT%H:%M:%SZ').astimezone(datetime.timezone.utc)


def configureKibana():
    print("Configuring Kibana...")
    dismissXPackBanner()
    indexId = createIndexPattern()
    setAsDefaultIndexPattern(indexId)

    return {
        "indexId": indexId
    }


def dismissXPackBanner():
    response = requests.post(
        getKibanaUrl("/api/kibana/settings/xPackMonitoring:showBanner"),
        headers={"Content-Type": "application/json;charset=UTF-8", "kbn-version": "6.2.4"},
        json={"value": False}
    )

    response.raise_for_status()


def createIndexPattern():
    response = requests.post(
        getKibanaUrl("/api/saved_objects/index-pattern"),
        headers={"Content-Type": "application/json;charset=UTF-8", "kbn-version": "6.2.4"},
        json={
            "attributes": {
                "title": "logs",
                "timeFieldName": "@timestamp"
            }
        }
    )

    response.raise_for_status()

    return response.json()["id"]


def setAsDefaultIndexPattern(id):
    response = requests.post(
        getKibanaUrl("/api/kibana/settings/defaultIndex"),
        headers={"Content-Type": "application/json;charset=UTF-8", "kbn-version": "6.2.4"},
        json={
            "value": id
        }
    )

    response.raise_for_status()


def generateViewerUrl(logDetails, kibanaDetails):
    kibanaIndexId = kibanaDetails["indexId"]

    columns = [
        "@threadName",
        "@severity",
        "@message"
    ]

    formattedColumns = joinCommaSeparated(map(quote, columns))

    startTime = toStartOfSecond(logDetails["minTimestamp"]).strftime('%Y-%m-%dT%H:%M:%S.%fZ')
    endTime = toEndOfSecond(logDetails["maxTimestamp"]).strftime('%Y-%m-%dT%H:%M:%S.%fZ')

    args = {
        "_g": "(refreshInterval:(display:Off,pause:!f,value:0),time:(from:'{startTime}',mode:absolute,to:'{endTime}'))".format(
            startTime=startTime,
            endTime=endTime
        ),
        "_a": "(columns:!({columns}),filters:!(),index:'{indexId}',interval:auto,query:(language:lucene,query:''),sort:!('logLineNumber',asc))".format(
            indexId=kibanaIndexId,
            columns=formattedColumns
        )
    }

    urlParts = (
        "http",
        "localhost:5601",
        "app/kibana#/discover",
        "",
        urllib.parse.urlencode(args),
        "",
    )

    return urllib.parse.urlunparse(urlParts)


def quote(value):
    return "'" + value + "'"


def joinCommaSeparated(values):
    return ",".join(values)


def toStartOfSecond(time):
    return time.replace(microsecond=0)


def toEndOfSecond(time):
    return (time + datetime.timedelta(seconds=1)).replace(microsecond=0)


def waitForExit():
    input("Press Enter to exit.\n")


def getElasticsearchUrl(url):
    return os.getenv("ELASTICSEARCH", "http://elasticsearch:9200") + url


def getKibanaUrl(url):
    return os.getenv("KIBANA", "http://kibana:5601") + url


if __name__ == '__main__':
    main()
