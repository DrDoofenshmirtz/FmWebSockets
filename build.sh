#! /usr/bin/env sh

echo "--- Build project 'FmWebsockets' ---"

echo "Clean up compilation output path..." 
rm -rf ./classes/*
echo "...done."

echo "Compile project..."
lein compile
echo "...done."

echo "Create jar..."
lein jar
echo "...done."

echo "Move jar to lib folder..."
mv fm-websockets.jar ./dist/lib/
echo "...done."

echo "--- Bye! ---"

