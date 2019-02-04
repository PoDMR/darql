echo total: $(zcat streaksTripleCount.csv.gz | wc -l)
echo '>1': $(zcat streaksTripleCount.csv.gz | grep , | wc -l)
echo A: $(zcat streaksTripleCount.csv.gz | grep , | grep [^-][0-9] | wc -l)
echo A1: $(zcat streaksTripleCount.csv.gz |grep , | grep [^-][0-9] | grep - | wc -l)
echo A2: $(zcat streaksTripleCount.csv.gz | sed -nr '/-[0-9]+,[0-9]+/p' | wc -l)
echo A2r: $(zcat streaksTripleCount.csv.gz | sed -nr '/[^-][0-9]+,-[0-9]+/p' | wc -l)
echo
echo 'maxElement -> count'
python - <(zcat streaksTripleCount.csv.gz) <<EOF | sort -n
import sys
with open(sys.argv[1]) as file:
    lines = file.readlines()
    m = {}
    for line in lines:
        line = line.strip()
        arr = line.split(',')
        nums = [int(x) for x in arr]
        num = max(nums)
        m[num] = m.get(num, 0) + 1
    for k, v in m.iteritems():
        print k, v
EOF
echo
printf 'Streaks with changes: '
python - <(zcat streaksTripleCount.csv.gz | sed -r '/^((-[0-9]+),?)+$/d') <<EOF | wc -l
import sys
with open(sys.argv[1]) as file:
    lines = file.readlines()
    for line in lines:
        line = line.strip()
        arr = line.split(',')
        nums = [int(x) for x in arr]
        a_set = set(nums)
        if (len(a_set) > 1):
            print(line)
EOF

printf 'Average triple count: '
python - <(zcat streaksTripleCount.csv.gz) <<EOF
import sys
with open(sys.argv[1]) as file:
    lines = file.readlines()
    tc_sum = 0
    tc_count = 0
    for line in lines:
        line = line.strip()
        arr = line.split(',')
        nums = [int(x) for x in arr]
        p_nums = [num for num in nums if num > 0]
        tc_sum += sum(p_nums)
        tc_count += len(p_nums)
    print(1. * tc_sum / tc_count)
EOF

echo s: $(zcat streaksShape.csv.gz | grep s | wc -l)
echo s only: $(zcat streaksShape.csv.gz | grep -P '^(s,?)+$' | wc -l)
echo b: $(zcat streaksShape.csv.gz | grep b | wc -l)
echo b only: $(zcat streaksShape.csv.gz | grep -P '^(b,?)+$' | wc -l)
echo c: $(zcat streaksShape.csv.gz | grep c | wc -l)
echo c only: $(zcat streaksShape.csv.gz | grep -P '^(c,?)+$' | wc -l)

echo b and c: $(zcat streaksShape.csv.gz | grep b | grep c | wc -l)
echo only b or c: $(zcat streaksShape.csv.gz | grep -P '^([bc],?)+$' | wc -l)
echo b and s: $(zcat streaksShape.csv.gz | grep b | grep s | wc -l)
echo only b or s: $(zcat streaksShape.csv.gz | grep -P '^([bs],?)+$' | wc -l)
echo s and c: $(zcat streaksShape.csv.gz | grep s | grep c | wc -l)
echo only s or c: $(zcat streaksShape.csv.gz | grep -P '^([sc],?)+$' | wc -l)
echo only s,b,c: $(zcat streaksShape.csv.gz | grep -P '^([sbc],?)+$' | wc -l)

echo 'c and >1': $(zcat streaksShape.csv.gz | grep c | grep -v , | wc -l)
echo 'only =1': $(zcat streaksShape.csv.gz | grep -v , | wc -l)
