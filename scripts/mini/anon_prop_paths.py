import re
import fileinput

for line in fileinput.input():
    line = line.strip()
    if line:
        a_list = re.findall("<[^>]+>", line)
        set0 = set()
        set1 = [x for x in a_list if not (x in set0 or set0.add(x))]
        (a_str, a_char) = (line, 'a')
        for symbol in set1:
            a_str = a_str.replace(symbol, a_char)
            a_char = chr(ord(a_char) + 1)
        print(a_str)
