set -e -u
shopt -s expand_aliases
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
alias detkdecomp=detkdecomp-linux64
alias make_hg="groovyclient $SCRIPT_DIR/JenaHg.groovy"
alias delim_split="python $SCRIPT_DIR/delim_split.py"

split() {
	local split_dir="$1"
	local cat_file="$2"
	if [ ! -d "${split_dir}" ]; then
		mkdir -p "${split_dir}"
		delim_split "$split_dir" .sparql '####' < "${cat_file}"
	fi
}

split_all() {
	local split_dir="$1"
	local cat_dir="$2"
	for file in "${cat_dir}"/*/shapeless_fo*; do
		local sub_dir=$(realpath --relative-to="${cat_dir}" "${file}")
		local split_dir2="${split_dir}/0/${sub_dir}"
		split_dir2="${split_dir2%.*}"
		split "${split_dir2}" "${file}"
	done
}

process_p1() {
	local filename="$1"
	local width="$2"
	local sp_file="${filename%.*}.sparql"
	local hg_file="${filename%.*}.dtl"
	local er_file="${filename%.*}.fail"
	local gml_file="${filename%.*}.gml"
	local map_file="${filename%.*}.yaml"
	if [[ ! -f "${hg_file}" ]]; then
		make_hg "${sp_file}" 1> "${hg_file}" 2> "${map_file}" < /dev/null
	fi
	if [[ ! -f "${er_file}" ]] && [[ ! -f "${gml_file}" ]]; then
		detkdecomp ${width} "${hg_file}" < /dev/null
	fi
	if [[ ! -f ${gml_file} ]]; then
		touch "${er_file}" < /dev/null
	fi
}

process_p2() {
	local filename="$1"
	local sp_file="${filename%.*}.sparql"
	local gml_file="${filename%.*}.gml"
	if [[ ! -f ${gml_file} ]]; then
		printf "${sp_file}\n"
	fi
}

make_next() {
	local split_dir="$1"
	local num="$2"
	local next_num=$((num+1))
	local target_dir="${split_dir}/${next_num}"
	[ ! -d "${target_dir}" ] && mkdir "${target_dir}"  # < /dev/null
	local file2
	while read -r file2; do
		local rel_name=$(realpath --relative-to="${split_dir}/${num}" "${file2}")
		local target_file="${target_dir}/${rel_name}"
		mkdir -p $(dirname "${target_file}") < /dev/null
		cp ${file2} "${target_file}" < /dev/null
	 done #< /dev/null
}

process_all() {
	local split_dir="$1"
	local num=1
	[ ! -d "${split_dir}/1" ] && cp -r "${split_dir}/0" "${split_dir}/1"
	while true; do
		local cur_dir="${split_dir}/${num}"
		local tmp_file=${split_dir}/tmp.txt # $(mktemp)
		> "${tmp_file}"
		local file
		find "${split_dir}/${num}" -type f -iname '*.sparql' -print0 | sort -z |
				while read -d $'\0' file; do
			process_p1 "${file}" $((num+1))
			process_p2 "${file}" >> "${tmp_file}"
		done
		make_next "${split_dir}" "${num}" < "${tmp_file}"
		num=$((num+1))
		next_count=$(find "${split_dir}/${num}" -type f | wc -l)
		[ ! "${next_count}" -gt 0 ] && break
		[ "${num}" -gt 20 ] && break
	done
}

summary() {
	local split_dir="$1"
	for file in "${split_dir}"/*; do
		[ ! -d "${file}" ]&& continue
		local count=$(find "${file}" -type f -iname '*.gml' | wc -l)
		local key=$(basename "${file}")
		[ "${key}" -le 0 ]&& continue
		[ "${count}" -le 0 ]&& continue
		key=$((key+1))
		printf "${key}: ${count}\n"
	 done
}

main() {
	local split_dir="$1"
	local cat_dir="$2"
	split_all "${split_dir}" "${cat_dir}"
	process_all "${split_dir}"
	summary "${split_dir}"
}

[[ "$@" ]] && main $1 $2
