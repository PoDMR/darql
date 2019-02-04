notriv_counts() { sed -r '/^((-[0-9]+),?)+$/d' */streaksTripleCount.csv | sed -nr '/(.*,){2}/p'; }

python - <(notriv_counts) <<EOF | less
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


notriv_counts | grep - | wc -l
notriv_counts | sed -nr '/-[0-9]+,[0-9]+/p' | wc -l
notriv_counts | sed -nr '/[0-9]+,-[0-9]+/p' | wc -l
notriv_counts | sed -nr '/-[0-9]+,[0-9]+/p' | sed -nr '/[0-9]+,-[0-9]+/p' | wc -l
notriv_counts | wc -l

python - <(notriv_counts) <<EOF | less
import sys
def compare(s1, s2):
    line1 = s1.strip()
    arr1 = line1.split(',')
    arr1 = [int(x) for x in arr1]
    line2 = s2.strip()
    arr2 = line2.split(',')
    arr2 = [int(x) for x in arr2]
    arr1 = sorted(arr1)
    arr2 = sorted(arr2)
    i = 0
    while i < len(arr1) and i < len(arr2):
        if (arr1[i] < arr2[i]):
            return -1
        elif (arr1[i] > arr2[i]):
            return 1
        else:
            i = i + 1
    if len(arr1) < len(arr2):
        return -1
    elif len(arr1) > len(arr2):
        return 1
    return 0  # TODO: should also look at unsorted version
with open(sys.argv[1]) as file:
    lines = file.readlines()
    lines = sorted(lines, cmp=compare)
    for line in lines:
        line = line.strip()
        arr = line.split(',')
        nums = [int(x) for x in arr]
        print(line)
EOF

notriv_shapes() { sed -r '/^(u,?)+$/d' */streaksShape.csv | sed -nr '/(.*,){2}/p'; }

notriv_shapes | sed -nr '/[b|s].*c/p' | wc -l
notriv_shapes | sed -nr '/c.*[b|s]/p' | wc -l

notriv_shapes | sed -nr '/s.*b/p' | wc -l
notriv_shapes | sed -nr '/b.*s/p' | wc -l

diff <(sed -nr '/^((-[0-9]+),?)+$/=' */streaksTripleCount.csv) \
 <(sed -nr '/^(u,?)+$/=' */streaksShape.csv)

{ sed -nr '/^((-[0-9]+),?)+$/=' */streaksTripleCount.csv ;
 sed -nr '/^(u,?)+$/=' */streaksShape.csv ; } |
 sort -n | uniq -u | wc -l

vimdiff *4/streaksTripleCount.csv *4/streaksShpe.csv


echo total: $(zcat streaksTripleCount.csv.gz | wc -l)
echo '>1': $(zcat streaksTripleCount.csv.gz | grep , | wc -l)
echo A: $(zcat streaksTripleCount.csv.gz | grep [^-][0-9] | wc -l)
echo A1: $(zcat streaksTripleCount.csv.gz | grep [^-][0-9] | grep - | wc -l)
echo A2: $(zcat streaksTripleCount.csv.gz | sed -nr '/-[0-9]+,[0-9]+/p' | wc -l)
echo A2r: $(zcat streaksTripleCount.csv.gz | sed -nr '/[0-9]+,-[0-9]+/p' | wc -l)

echo 'max -> count'
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

zcat streaksTripleCount.csv.gz | sed -r 's/^-[0-9],|,?-[0-9]//g' | grep -v '^$' | while IFS= read -r line; do
  s=$(printf '%s\n' "$line" | tr ',' '+' | bc)
  n=$(printf '%s,\n' "$line" | grep -o , | wc -l)
  bc -l <<< "$s/$n"
done | gawk '{a+=$1} END {print a/NR}'

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

cat */streaksTripleCount.csv | gzip - > ~/Downloads/streak/streaksTripleCount.csv.gz
cat */streaksShape.csv | gzip - > ~/Downloads/streak/streaksShape.csv.gz
