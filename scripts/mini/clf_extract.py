import re
import fileinput
import urlparse

pattern = '[^"]*"(?:GET )?/sparql/?\?([^"\s]*)[^"]*".*'

def process_lines():
    for line in fileinput.input():
        line = line.strip()
        if line:
            handle_clf(line)

def handle_clf(line):
    match_obj = re.match(pattern, line)
    if match_obj:
        handle_url_param(match_obj.groups()[0])

def handle_url_param(url_str):
    map = dict(urlparse.parse_qsl(url_str))
    queryStr = map.get("query")
    if queryStr:
        print(queryStr)

process_lines()
