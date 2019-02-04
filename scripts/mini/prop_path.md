```bash
anon_prop_paths.py /data/khan/arq/output/batch/170706165629/*/prop_paths.txt |
  sort | uniq -c | sort -nr > prop_path_count.tsv
```
