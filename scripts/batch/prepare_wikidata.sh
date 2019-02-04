set -e -u

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
pwd=$PWD
[[ ! -z "${PRIME_DIR}" ]] && out_dir=${PRIME_DIR}/out
PRIME_DIR=${PRIME_DIR:-/data/khan}
wd_prefix=${PRIME_DIR}/wd/wd_logs
arc_dir=${wd_prefix}/arc
raw_dir=${wd_prefix}/raw
dedup_dir=${wd_prefix}/dedup
out_dir=${out_dir:-${PRIME_DIR}/arq/output}
src_dir=$PWD

ensure_dir() {
  [ ! -d ${1} ] && mkdir -p ${1}
}

download() {
  ensure_dir ${arc_dir} && cd ${arc_dir}
  lftp https://analytics.wikimedia.org/datasets/one-off/wikidata/sparql_query_logs/ -e "ls; exit"
  ensure_dir ${raw_dir}
  cd ${raw_dir}


  unpack() {
    cd ${arc_dir}
    for f in */*.gz; do
      t_dir=${raw_dir}/$(dirname ${f})
      ensure_dir ${t_dir}
      if [ ! -f ${raw_dir}/${f} ]; then
        echo "Extracting ${f}"
        gzip -kdc ${f} | pv > ${raw_dir}/${f}
      fi
    done
  }
  unpack

  grep_split() {
    ex=$1
    filter=$2
    out=$3
    for f in */*_${ex}; do
      outfile=$(echo ${f} | sed -r 's#(.*/[^/]*)'_${ex}'#\1#g')_${out}.tsv
      if [ ! -f  ${outfile} ]; then
        echo "grepping '${filter}' in ${f}"
        pv ${f} | grep $'\t'${filter} > ${outfile}
      fi
    done
  }
  cd ${arc_dir}
  grep_split all robotic robotic
  grep_split all organic organic_grep
  grep_split status_500 robotic robotic_500
  grep_split status_500 organic organic_500
}

dedup() {
  cd ${pwd}
  single() {
    jobname=$1
    outbasename=$2
    if [ ! -d ${dedup_dir}/${outbasename} ]; then
      echo "deduplicating ${jobname}"
      JOB_NAME=${jobname} JOB_SET=dedup make work
      mkdir ${dedup_dir}/${outbasename}
      mv ${out_dir}/uniq.log ${dedup_dir}/${outbasename}
    fi
  }
  single wd17_raw_organic_grep organic_grep
  single wd17_raw_robotic robotic
  single wd17_raw_organic_500 organic_500
  single wd17_raw_robotic_500 robotic_500
}

install_tools() {
  ensure_dir ~/bin
  if [ ! -f ~/bin/detkdecomp ]; then
    ensure_dir ${src_dir} && cd ${src_dir}
    git clone https://github.com/daajoe/detkdecomp
    cd detkdecomp/source
    git checkout f58ab63
    make
    cp ./detkdecomp ~/bin
  fi
  add_if_missing() { echo $PATH | grep -q $1 || export PATH=$1:$PATH; }
  add_if_missing ~/bin

  [ $(which sdk) ] && curl -s https://get.sdkman.io | bash
  [ $(which java) ] && sdk install java
  [ $(which mvn) ] && sdk install mvn
  [ $(which groovy) ] && sdk install groovy
  [ $(which gradle) ] && sdk install gradle
  [ $(which python3) ] && curl https://repo.continuum.io/miniconda/Miniconda3-latest-Linux-x86_64.sh | bash
  apted
  if [ ! -f ~/bin/jdrasil.exact ]; then
    ensure_dir ${src_dir} && cd ${src_dir}
    git clone https://github.com/maxbannach/Jdrasil
    cd Jdrasil
    git checkout c079df1
    ./gradlew
    if [ $(which native-image) ]; then
      ./gradlew exact && cp tw−exact jdrasil.exact
    else
      native-image --no-server -cp build/jars/Jdrasil.jar jdrasil.Exact
    fi
    cp ./jdrasil.exact ~/bin
  fi
}

apted() {
  [ -d ~/.m2/repository/at/unisalzburg/dbresearch/apted/ ] && return 0

  git clone https://github.com/DatabaseGroup/apted
  cd apted
  git checkout 193666b

tee -a build.gradle <<"EOF"
apply plugin: 'maven-publish'
group 'at.unisalzburg.dbresearch'
//artifactId = 'apted'
version = '193666b'

publishing {
  repositories {
      mavenLocal()
  }
  publications {
      maven(MavenPublication) {
          artifact "${project.buildDir}/libs/${project.name}.jar"
      }
  }
}
EOF

  gradle build publishToMavenLocal
}

setup() {
  ensure_dir ${src_dir} && cd ${src_dir}
  [ ! -d ${src_dir}/arq ] && return 0

  git clone https://github.com/PoDMR/darql/ arq && cd arq

  sed -e 's/^HOST :=.*/^HOST := khan/g' -i Makefile
  sed -e 's#/data/khan#'${PRIME_DIR}'#g' \
      -i src/main/resources/config.yaml
  sed -e 's#/home/khan/work/code/arq#'${src_dir}'#g' \
      -e 's#/data/khan/arq#'${PRIME_DIR}/arq'#g' \
      -i src/main/resources/com/gitlab/ctt/arq/local-khan.prop
  mv src/main/resources/com/gitlab/ctt/arq/local-khan.prop \
     src/main/resources/com/gitlab/ctt/arq/local-$(hostname).prop

  sed -e 's#basedir=/data/khan/arq/output/batch#'${out_dir}/batch'#' \
      -e 's#outprefix=~/Downloads/out#'${out_dir}/xlsx'#' \
      -e 's#ARQ=~/work/code/arq#'${src_dir}'g' \
      -i .files/scripts/batch_wikidata.sh
}

if [ ${#@} -le 0 ]; then
  install_tools
  download
  setup
  dedup
  bash ${SCRIPT_DIR}/scripts/batch/batch_wikidata.sh ${out_dir}/batch ${out_dir}/xlsx
fi

