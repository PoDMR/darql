import re
import sys

f = None
out_dir = sys.argv[1]
ext = sys.argv[2]
delim = sys.argv[3]
pattern = '^' + delim + '(.*)'

def process_lines():
    i = 0
    for line in sys.stdin:
        line = line.strip()
        if line:
            match_obj = re.match(pattern, line)
            if match_obj:
                i = i + 1
                filename = out_dir + '/' + str(i) + ext
                f = open(filename, 'w')
            else:
                f.write(line + '\n')

process_lines()
