#!/bin/bash

git init
git remote add origin https://github.com/b51235423/MegaApiTest.git
git add .
git rm -r -f out/
git status .
git commit
git push -u origin master
