mkdir dbpedia_12 dbpedia_13 dbpedia_14 dbpedia_15 dbpedia_16 dbpedia_17
mkdir swdf_uw13 biomed_uw13 bioportal_uw13 bioportal_uw14 lgd_uw13 lgd_uw14
mkdir RKBE_tsv wikidata

prepare() {
  obtain() { wget ${url}/$1 }
  cd ${src_dir}
  obtain Archive.zip               # p8/pcloud-t
  obtain 'Archive 2.zip'           # p8/pcloud-t
  obtain usewod2013_dataset.tar    # p8/pcloud-z
  obtain usewod2014-dataset.tar    # p8/pcloud-z
  obtain usewod2015-dataset.tar    # p8/pcloud-z
  obtain usewod2016-dataset.tar    # p8/pcloud-z
  obtain dbpedia-2015.tar          # p8/pcloud-z
  obtain dbpedia-2016-04-logs.tar  # rnd
  obtain dbpedia-2016-10-logs.tar  # rnd
  cd -
}

guard() { [ ! -f $1 ]; }
get() { cp -al "${src_dir}/$1" .; }

if guard wikidata/wikidata_examples.txt; then
  wget https://raw.githubusercontent.com/PoDMR/darql/master/src/main/resources/sample/demo/wikidata.txt
  mv wikidata.txt wikidata/wikidata_examples.txt
fi

if guard RKBE_tsv/*; then
  get RKB-Explorer.7z
  7z -x RKB-Explorer.7z
  rm RKB-Explorer.7z
  gzip -d RKB-Explorer/RKBEndpointsLogs/'second log'/sparql.log*.gz
  mkdir RKBE_tsv/{first,2nd}
  mv -t "RKBE_tsv/first" RKB-Explorer/RKBExplorer/sparql.log.[1-9][0-1]*
  mv -t "RKBE_tsv/2nd" RKB-Explorer/RKBEndpointsLogs/'second log'/sparql.log
  rm -rf RKB-Explorer
fi

if guard dbpedia_13/2013-??-??_angela14.log; then
  get Archive.zip
  unzip Archive.zip
  rm Archive.zip
  gzip -d http????2013.log.gz
  gzip -d http????2014.log.gz
  mv http????2013.log dbpedia_13
  mv http????2014.log dbpedia_14
  get 'Archive 2.zip'
  unzip 'Archive 2.zip'
  rm 'Archive 2.zip'
  gzip -d http????2013.log.gz
  gzip -d http????2014.log.gz
  mv http????2013.log dbpedia_13
  mv http????2013.log dbpedia_14
  rename 's#dbpedia_13/http(??)(??)2013.log#dbpedia_13/2013-$1-$2_angela14.log#'
  rename 's#dbpedia_14/http(??)(??)2014.log#dbpedia_13/2014-$1-$2_angela14.log#'
fi

if guard dbpedia_12/*_usewod13.log; then
  get usewod2013_dataset.tar
  tar -xvf usewod2013_dataset.tar
  rm usewod2013_dataset.tar
  bzip2 -d usewod2013_dataset/data/CLF-server-logs/dbpedia/*/20{09,10,11,12}-??-??.log.bz2
  bzip2 -d usewod2013_dataset/data/CLF-server-logs/swdf/20{08,10,11,12,13}-??-??.log.bz2
  bzip2 -d usewod2013_dataset/data/CLF-server-logs/lgd/2011-??-??.log.bz2
  bzip2 -d usewod2013_dataset/data/SPARQL-endpoint-logs/bioportal/201{2,3}-??-??.log
  bzip2 -d usewod2013_dataset/data/SPARQL-endpoint-logs/openbiomed/2012-1{1,2}-??.log.bz2
  mv -t dbpedia_12     usewod2013_dataset/data/CLF-server-logs/dbpedia/*/20{09,10,11,12}-??-??.log
  mv -t lgd_uw13       usewod2013_dataset/data/CLF-server-logs/lgd/2011-??-??.log
  mv -t swdf_uw13      usewod2013_dataset/data/CLF-server-logs/swdf/20{08,10,11,12,13}-??-??.log
  mv -t bioportal_uw13 usewod2013_dataset/data/SPARQL-endpoint-logs/bioportal/201{2,3}-??-??.log
  mv -t biomed_uw13    usewod2013_dataset/data/SPARQL-endpoint-logs/openbiomed/2012-1{1,2}-??.log
  rm -rf usewod2013_dataset
  rename 's#dbpedia_12/(.*).log#dbpedia_12/$1_usewod13.log#'
  rename 's#biomed_uw13/(....)-(..)-(..).log#biomed_uw13/$1-$2-$3_sparql.log#'
  rename 's#bioportal_uw13/(....)-(..)-(..).log#bioportal_uw13/$1-$2-$3_sparql.log#'
  rename 's#bioportal_uw13/(....)-(..)-(..).log#bioportal_uw13/$1-$2-$3_sparql.log#'
fi

if guard dbpedia_13/*_usewod14.log; then
  get usewod2014-dataset.tar
  tar -xvf usewod2014-dataset.tar
  rm usewod2014-dataset.tar
  gzip -d USEWOD2014/data/DBpedia/dbpedia3.{8,9}/http????2013.log.gz
  gzip -d USEWOD2014/data/BioPortal/query-bioportal.log-201{2,3,4}????-sparql.gz
  mv -t bioportal_uw14 USEWOD2014/data/BioPortal/query-bioportal.log-201{2,3,4}????-sparql
  bzip2 -d USEWOD2014/data/LinkedGeoData/2014-*-from-2012*.log.bz2
  mv -t lgd_uw14 USEWOD2014/data/LinkedGeoData/2014-*-from-2012*.log
  rm -rf USEWOD2014
fi

if guard dbpedia_14/*_usewod15.log; then
  get usewod2015-dataset.tar
  tar -xvf usewod2015-dataset.tar
  rm usewod2015-dataset.tar
  tar -xvf usewod-2015/usewod-2015-dbpedia-3.9.tar
  gzip -d usewod-2015/usewod-2015-dbpedia-3.9/access.log-2014????.gz
  mv -t dbpedia_14 usewod-2015/usewod-2015-dbpedia-3.9/access.log-2014????
  rename 's#usewod-2015/access.log-2014(..)(..)#usewod-2015/2014-$1-$2_usewod15.log#'
  rm -rf usewod-2015
fi

if guard usewod-2015/*_usewod16.log; then
  get usewod2016-dataset.tar
  tar -xvf usewod2016-dataset.tar
  rm usewod2016-dataset.tar
  tar -xvf usewod2016-dataset/usewod-2016-dbpedia-3.9.tar
  bzip2 -d usewod2016-dataset/usewod-2016-dbpedia-3.9/access.log-2015????.bz2
  mv -t dbpedia_15 usewod2016-dataset/usewod-2016-dbpedia-3.9/access.log-2015????
  rm -rf usewod2016-dataset
  rename 's#usewod2016-dataset/access.log-2015(..)(..)#usewod-2015/2015-$1-$2_usewod16.log#'
fi

if guard dbpedia_16/*_angela16; then
  get dbpedia-2015.tar
  tar -xvf dbpedia-2015.tar
  rm dbpedia-2015.tar
  bzip2 -d dbpedia-2015/access.log-201{5,6}????.bz2
  mv -t dbpedia_16 dbpedia-2015/access.log-201{6}????
  rm -rf dbpedia-2015
  rename 's#dbpedia_16/access.log-2016(..)(..)#dbpedia_16/2016-$1-$2_angela16.log#'
fi

if guard dbpedia_17/*; then
  get dbpedia-2016-04-logs.tar
  tar -xvf dbpedia-2016-04-logs.tar
  rm dbpedia-2016-04-logs.tar
  gzip -d access.log-2017????.gz
  mv dbpedia-2016-04-logs/access.log-2017???? dbpedia_17
  rm -rf dbpedia-2016-04-logs
  get dbpedia-2016-10-logs.tar
  tar -xvf dbpedia-2016-10-logs.tar
  rm dbpedia-2016-10-logs.tar
  gzip -d access.log-2017????.gz
  mv dbpedia-2016-10-logs/access.log-2017???? dbpedia_17
  rm -rf dbpedia-2016-10-logs
fi
