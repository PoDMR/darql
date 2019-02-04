import re
import fileinput

pattern = '^# (.*)'
f = None
out_dir = 'out' # if not len(sys.argv) > 2 else sys.argv[2]

def process_lines():
    for line in fileinput.input():
        line = line.strip()
        if line:
            handle_line(line)

def handle_line(line):
    global f
    match_obj = re.match(pattern, line)
    if match_obj:
        n = match_obj.groups()[0]
        filename = out_dir + '/' + n + '.tsv'
        print filename
        f = open(filename, 'w')
    else:
        f.write(line + '\n')

process_lines()
