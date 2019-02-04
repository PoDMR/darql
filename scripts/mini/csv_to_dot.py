import re
import fileinput
import sys

def process_lines():
    files = sys.argv[1:]
    if not files: files = tuple("-", )

    for line in fileinput.input(files):
        line = line.strip()
        if line:
            handle_line(line)

def handle_line(line):
    global f
    match_obj = re.match('(\d)+ (\d)+ (\d)+', line)
    if match_obj:
        a = match_obj.groups()[0]
        b = match_obj.groups()[1]
        c = match_obj.groups()[2]
        print("{} -> {} [label={}]".format(a, c, b))

print("digraph G {")
process_lines()
print("}")
