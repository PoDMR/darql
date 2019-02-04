set -e -u

iterate() {
	local base="$1"
	echo "src,n,max,upper,median,lower,min"
	for adir in "${base}"/*; do
		if [ -d ${adir} ]; then
			local num=$(basename "${adir}")
			num=${num##[a-z]}
			for fn in "${adir}"/bench_*.csv; do
				local id=${fn}
				id=${id##*_}
				id=${id%.*}
				transform "${fn}" "${id}" "${num}"
			done
		fi
	done
}

transform() {
	local file="$1"
	local src="$2"
	local num="$3"
	python - "${file}" "${src}" "${num}" <<-EOF
		import sys
		filename = sys.argv[1]
		id = sys.argv[2]
		num = sys.argv[3]
		with open(filename) as file:
		    lines = file.readlines()
		    nums = [float(x) for x in lines]  # alt: float=int
		    nums.sort()
		    l = len(nums)
		    out=[
		        id,
		        num,
		        max(nums),
		        nums[int(round(l * 0.75))],
		        nums[int(round(l / 2))],
		        nums[int(round(l * 0.25))],
		        min(nums)
		        ]
		    line=",".join(str(x) for x in out)
		    print(line)
	EOF
}


extract() {
	local base="$1"
	for fn in "${base}"/*/bench.tar.gz; do
		local adir=$(dirname "${fn}")
		tar -xzf "${fn}" -C "${adir}"
	done
}

main() {
	compgen -G "$1"/b*/bench_*.csv > /dev/null || extract "$1"
	iterate "$1" # > gmark.csv
}

[[ "$*" ]] && main $1
echo $(basename $0) done at $(date)
