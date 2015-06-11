#!/usr/bin/env python

import codecs
import json
import requests
import re
import sys
import time

migrated = []

pattern = re.compile("Page ID (\d+) was migrated to page ID (\d+)")

url = "https://graph.facebook.com?fields=location,name&ids="

with open("export_locations.csv") as f:
    content = map(lambda l: l.rstrip('\n'), f.readlines())

chunks=[content[x:x+1000] for x in xrange(0, len(content), 1000)]

for index, chunk in enumerate(chunks):
  while True:
    time.sleep(1)

    joined = ",".join(chunk)
    r = requests.get(url + joined)
    body = r.json()

    if( r.status_code == 200 ):
      break

    if( r.status_code == 400 and body["error"]["code"] == 21 ):
      match = pattern.match(body["error"]["message"])
      invalidId = str(match.group(1))
      newId = str(match.group(2))
      chunk = filter(lambda id: id != invalidId, chunk)
      chunk.append(newId)
      migrated.append("{0} - {1}".format(invalidId, newId))
      continue

    sys.exit(1)

  parsed = []
  for attribute, value in body.iteritems():
    location = value["location"]

    name = value["name"].encode('utf-8')
    latitude = (location["latitude"] if "latitude" in location else "")
    longitude = (location["longitude"] if "longitude" in location else "")
    code = (location["zip"].encode('utf-8') if "zip" in location else "")

    parsed.append("{0};{1};{2};{3};{4}\n".format(value["id"], name, latitude, longitude, code))

  with open('output_{0}'.format(str(index).zfill(3)), 'w') as file_:
    file_.write("".join(parsed))

with open('output_migrated', 'w') as file_:
  file_.write("\n".join(migrated))

print "{0} ids".format(len(content))