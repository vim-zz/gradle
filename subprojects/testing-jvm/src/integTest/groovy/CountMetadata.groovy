/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class CountMetadata {
    static Map<String, Map<String, Integer>> metadata = new HashMap<>()
    static List<String> keysToCount = ['type', 'operatingSystem']

    static void main(String[] args) {
        File artifactDir = new File(args[0])
        assert artifactDir.name == 'artifacts-v2'
        artifactDir.listFiles { File file ->
            file.isDirectory()
        }.each {
            checkDir(it)
        }

        println(metadata)
    }

    static void checkDir(File dir) {
        println("Processing $dir...")
        println("Metadata so far: $metadata")
        dir.listFiles { File file ->
            file.name.endsWith(".artifact")
        }.each { File file ->
            "mkdir -p /tmp/${file.name}".execute().waitFor()
            "tar xf ${file.absolutePath} --directory=/tmp/${file.name}".execute().waitFor()
            Properties properties = new Properties()
            properties.load(new FileInputStream(new File("/tmp/${file.name}/METADATA")))

            for (String key in keysToCount) {
                String value = properties.getOrDefault(key, "null").toString()
                metadata.putIfAbsent(key, new HashMap<String, Integer>())
                Map<String, Integer> countMap = metadata.get(key)

                countMap.put(value, countMap.getOrDefault(value, 0) + 1)
            }
            "rm -rf /tmp/${file.name}".execute().waitFor()
        }
    }
}
