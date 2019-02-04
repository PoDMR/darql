set -e -u

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
pwd=$PWD
[[ ! -z "${PRIME_DIR}" ]] && out_dir=${PRIME_DIR}/out
PRIME_DIR=${PRIME_DIR:-/data/khan}
raw_dir=${PRIME_DIR}/arq/input2 # ${wd_prefix}/raw
dedup_dir=${PRIME_DIR}/arq/output/uniq # ${wd_prefix}/dedup
out_dir=${out_dir:-${PRIME_DIR}/arq/output}
src_dir=$PWD


dedup-dbp() {
  cd ${pwd}
  single_dedup() {
    jobname=raw_mod
    filter=$1
    outbasename=$2
tee src/main/resources/config_u.yaml <<EOF
input:
  - key: raw_mod
    src:
      - host: khan
        dir: ${PRIME_DIR}/arq/input2
    filter: |-
      ${filter}/.*
job_sets:
  - name: default
    jobs:
    - pathExtractor
    - propertyCounter
    - opDistribution2
    - opDistribution
EOF
    if [ ! -d ${dedup_dir}/${outbasename} ]; then
      echo "deduplicating ${jobname}"
      env EXEC_ARGS := -Dexec.args="src/main/resources/config_u.yaml" \
        JOB_NAME=${jobname} JOB_SET=dedup make work
      mkdir ${dedup_dir}/${outbasename}
      mv ${out_dir}/uniq.log ${dedup_dir}/${outbasename}
    fi
  }

  single_dedup  RKBE_tsv         RKBE
  single_dedup  dbpedia_12       uw13_dbpedia
  single_dedup  dbpedia_13       dbpedia_13
  single_dedup  dbpedia_14       dbpedia_14
  single_dedup  dbpedia_15       dbpedia_15
  single_dedup  dbpedia_16       dbpedia_16
  single_dedup  dbpedia_17       dbpedia_17
  single_dedup  lgd_uw13         uw13_lgd
  single_dedup  lgd_uw14         uw14_lgd
  single_dedup  swdf_uw13        uw13_swdf
  single_dedup  biomed_uw13      uw13_openbiomed_h
  single_dedup  bioportal_uw14   uw14_bioportal_r2
  single_dedup  bioportal_uw13   uw13_bioportal
  single_dedup  wikidata         wikidata
}

if [ ${#@} -le 0 ]; then
  source ${SCRIPT_DIR}/prepare_wikidata.sh no_run
  install_tools
  bash ${SCRIPT_DIR}/unpack_dbpedia.sh ${raw_dir}
  setup
  dedup-dbp
  bash ${SCRIPT_DIR}/scripts/batch/batch_dbpedia.sh  ${out_dir}/batch ${out_dir}/xlsx
fi
