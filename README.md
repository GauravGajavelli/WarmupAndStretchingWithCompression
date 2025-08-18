## Logger for CSSE230 Assignments
- See testSupport package

### Log File Schema
- run.tar
  - Tar of three log files
  - testRunInfo.json
    - These fields: 
      - [startTestRunInfo.json](https://github.com/GauravGajavelli/WarmupAndStretchingWithCompression/blob/main/src/testSupport/startTestRunInfo.json)
      - For each test: {"Test Filename": {"Test Name": {"Test Run Number": "Success/Failure"}}}
  - diffs.tar.zip
    - Two subdirectories
    - baselines/
      - Contains all (hashed) files outside of the testSupport package
      - Name formatting: package.className
    - patches/
      - Contains all diffs from current (hashed) file against baseline
      - Name formatting: package.className_testRunNumber
    - error-logs.txt
      - Just appends the first few hundred characters of the stack trace
