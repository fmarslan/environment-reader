
/**
 * 
 * Copyright 2019 FMARSLAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * 
 */

package com.fmarslan.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * @author fmarslan
 *
 */
public class EnvironmentReader {

	private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentReader.class);

	Map<String, Object> environments = new HashMap<String, Object>();
	boolean overrideEnv = true;

	private static EnvironmentReader instance;

	static {
		instance = new EnvironmentReader();
	}

	/***
	 * 
	 * 
	 * @param override for override with system environment [Default : TRUE]
	 */
	public static void setOverride(boolean override) {
		instance = new EnvironmentReader(override);
	}

	public static <T> T getEnvironment(String key) {
		return instance.get(key);
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String key) {
		if (environments.size() == 0)
			LOGGER.error("not loaded any properties");
		return (T) environments.get(key);
	}

	public static void loadFromFile(File file) {
		instance.load(file);
	}

	public static void loadFromResource(Class<?> clazz, String file) {
		instance.load(clazz, file);
	}

	public static void loadFromPath(String file) {
		instance.load(file);
	}

	public static void loadFromSystem() {
		instance.load();
	}

	public EnvironmentReader() {

	}

	/***
	 * 
	 * 
	 * @param overrideEnvironment for override with system environment [Default :
	 *                            TRUE]
	 */
	public EnvironmentReader(boolean overrideEnvironment) {
		overrideEnv = overrideEnvironment;
	}

	public void load(String file) {
		try {
			File fileObject = new File(file);
			if (fileObject.exists() == false) {
				LOGGER.error(String.format("%s file is not exists [%s]", file, fileObject.getPath()));
			} else {
				load(fileObject);
			}
		} catch (Exception e) {
			LOGGER.error("initProperties", e);
		}
	}

	public void load(Class<?> clazz, String file) {
		try {
			File fileObject = File.createTempFile(file, file.replaceAll("/", "_").replaceAll("\\", "_"));
			copyInputStreamToFile(clazz.getResourceAsStream(file), fileObject);
			if (fileObject.exists() == false) {
				LOGGER.error(String.format("%s file is not exists [%s]", file, fileObject.getPath()));
			} else {
				load(fileObject);
			}

		} catch (Exception e) {
			LOGGER.error("initProperties", e);
		}
	}

	public void load() {
		int maxKeyLength;
		initSystemEnvironments();
		maxKeyLength = environments.keySet().stream().map(x -> x.length()).max(Integer::compareTo).orElse(10);
		environments.keySet().stream().sorted(new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				return o1.compareToIgnoreCase(o2);
			}
		}).forEachOrdered(x -> {
			LOGGER.info(String.format("%-" + (maxKeyLength + 2) + "s:  %s", x, environments.get(x)));
		});
	}

	public void load(File file) {
		String fileName = file.getPath();
		if (fileName.lastIndexOf(".") > -1) {
			String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
			LOGGER.debug(String.format("environments reading from %s by %s file encoder", fileName, ext));
			switch (ext) {
			case "properties": {
				initPropertiesFile(file);
				break;
			}
			case "yaml": {
				initYMLFile(file);
				break;
			}
			case "yml": {
				initYMLFile(file);
				break;
			}
			case "xml": {
				initXMLFile(file);
				break;
			}
			default: {
				LOGGER.error(String.format("not supported file format [%s]", file.getName()));
			}
			}
			load();

		} else {
			LOGGER.error(String.format("not supported file format [%s]", file.getName()));
		}
	}

	protected void initSystemEnvironments() {
		try {
			System.getProperties().forEach((k, v) -> {
				if (overrideEnv || environments.containsKey(k) == false) {
					put(String.valueOf(k), v);
				}
			});
			System.getenv().forEach((k, v) -> {
				if (overrideEnv || environments.containsKey(k) == false) {
					put(String.valueOf(k), v);
				}
			});
		} catch (Exception e) {
			LOGGER.error("initSystemEnvironments", e);
		}
	}

	protected void initPropertiesFile(File file) {
		try {
			FileInputStream fileInput = new FileInputStream(file);
			Properties properties = new Properties();
			properties.load(fileInput);
			fileInput.close();

			Enumeration<?> enuKeys = properties.keys();
			while (enuKeys.hasMoreElements()) {
				String key = (String) enuKeys.nextElement();
				String value = properties.getProperty(key);
				put(key, value);
			}
		} catch (Exception e) {
			LOGGER.error("initPropertiesFile", e);
		}
	}

	protected void initXMLFile(File file) {
		try {
			FileInputStream fileInput = new FileInputStream(file);
			Properties properties = new Properties();
			properties.loadFromXML(fileInput);
			fileInput.close();

			Enumeration<?> enuKeys = properties.keys();
			while (enuKeys.hasMoreElements()) {
				String key = (String) enuKeys.nextElement();
				String value = properties.getProperty(key);
				put(key, value);
			}
		} catch (Exception e) {
			LOGGER.error("initXMLFile", e);
		}
	}

	protected void initYMLFile(File file) {
		try {
			Yaml yaml = new Yaml();
			try (FileInputStream fileInput = new FileInputStream(file)) {
				Map<String, Object> props = yaml.load(fileInput);
				fileInput.close();
				props.forEach((k, v) -> {
					Map<String, Object> subMap = nestedPropertyToFlatMap(k, v);
					putAll(subMap);
				});
			}
		} catch (Exception e) {
			LOGGER.error("initYMLFile", e);
		}
	}

	private Map<String, Object> nestedPropertyToFlatMap(String prefix, Object value) {
		if (value == null)
			return null;
		Map<String, Object> env = new HashMap<String, Object>();
		if (value instanceof Map<?, ?>) {
			@SuppressWarnings("unchecked")
			Map<String, Object> subMap = ((Map<String, Object>) value);
			subMap.forEach((k, v) -> {
				Map<String, Object> _sm = nestedPropertyToFlatMap(String.format("%s.%s", prefix, k), v);
				if (_sm != null)
					env.putAll(_sm);
				else
					env.put(prefix, null);
			});

		} else {
			env.put(prefix, value);
		}
		return env;
	}

	protected EnvironmentReader put(String key, Object value) {
		if (key == null || key.trim() == "")
			return this;
		environments.put(key, value);
		return this;
	}

	protected EnvironmentReader putAll(Map<String, Object> map) {
		environments.putAll(map);
		return this;
	}

	public static void main(String[] args) {
		EnvironmentReader pm = new EnvironmentReader();
		pm.load(pm.getClass(), "test.xml");
	}

	private static void copyInputStreamToFile(InputStream inputStream, File file) throws IOException {

		try (FileOutputStream outputStream = new FileOutputStream(file)) {

			int read;
			byte[] bytes = new byte[1024];

			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}

			// commons-io
			// IOUtils.copy(inputStream, outputStream);

		}

	}

}
