outDir="outout"
inputFile="samples/sample1.act"

find . -name "*.java" -print | xargs javac -classpath /usr/local/lib/antlr-4.7.2-complete.jar -d ${outDir}
java -classpath ./${outDir}:/usr/local/lib/antlr-4.7.2-complete.jar main.Acton $1

rm -rf ${outDir}
