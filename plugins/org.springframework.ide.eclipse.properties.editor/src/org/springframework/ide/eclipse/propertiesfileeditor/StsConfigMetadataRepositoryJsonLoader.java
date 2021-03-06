/*******************************************************************************
 * Copyright (c) 2014 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.propertiesfileeditor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.springframework.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.configurationmetadata.ConfigurationMetadataRepositoryJsonLoader;
import org.springframework.configurationmetadata.SimpleConfigurationMetadataRepository;

/**
 * Load a {@link ConfigMetadataRepository} from the content of an eclipse
 * projects classpath.
 *
 * @author Kris De Volder
 */
public class StsConfigMetadataRepositoryJsonLoader {

	/**
	 * The default classpath location for config metadata.
	 */
	public static final String[] META_DATA_LOCATIONS = {
		"META-INF/spring-configuration-metadata.json"
	};

	private SimpleConfigurationMetadataRepository repository = new SimpleConfigurationMetadataRepository();
	private ConfigurationMetadataRepositoryJsonLoader loader = new ConfigurationMetadataRepositoryJsonLoader();

	/**
	 * Load the {@link ConfigMetadataRepository} with the metadata of the current
	 * classpath using the {@link #DEFAULT_LOCATION_PATTERN}. If the same config
	 * metadata items is held within different resources, the first that is
	 * loaded is kept which means the result is not deterministic.
	 */
	public ConfigurationMetadataRepository load(IJavaProject project) throws Exception {
		IClasspathEntry[] classpath = project.getResolvedClasspath(true);
		for (IClasspathEntry e : classpath) {
			int ekind = e.getEntryKind();
			int ckind = e.getContentKind();
			IPath path = e.getPath();
			System.out.println(ekind(ekind)+" "+ckind(ckind)+": "+path);
			if (ekind==IClasspathEntry.CPE_LIBRARY && ckind==IPackageFragmentRoot.K_BINARY) {
				//jar file dependency
				File jarFile = path.toFile();
				if (isJarFile(jarFile)) {
					loadFromJar(jarFile);
				}
			}
			//TODO: project dependencies?
			//TODO: source folders?
		}
		return repository;
	}

	private void loadFromJar(File f) {
		JarFile jarFile = null;
		try {
			jarFile = new JarFile(f);
			//jarDump(jarFile);
			for (String loc : META_DATA_LOCATIONS) {
				ZipEntry e = jarFile.getEntry(loc);
				if (e!=null) {
					loadFrom(jarFile, e);
				}
			}
		} catch (Throwable e) {
			SpringPropertiesEditorPlugin.log(e);
		} finally {
			if (jarFile!=null) {
				try {
					jarFile.close();
				} catch (IOException e) {
				}
			}
		}
	}
	

	private void loadFrom(JarFile jarFile, ZipEntry ze) {
		InputStream is = null;
		try {
			is = jarFile.getInputStream(ze);
			ConfigurationMetadataRepository extra = loader.loadAll(Collections.singleton(is));
			repository.include(extra);
		} catch (Throwable e) {
			SpringPropertiesEditorPlugin.log(e);
		} finally {
			if (is!=null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private boolean isJarFile(File jarFile) {
		try {
			return jarFile!=null && jarFile.isFile() && jarFile.toString().toLowerCase().endsWith(".jar");
		} catch (Throwable e) {
			SpringPropertiesEditorPlugin.log(e);
			return false;
		}
	}

/// Debug utils
	private String ckind(int ckind) {
		switch (ckind) {
		case IPackageFragmentRoot.K_SOURCE:
			return "SRC";
		case IPackageFragmentRoot.K_BINARY:
			return "BIN";
		default:
			return ""+ckind;
		}
	}

	private String ekind(int ekind) {
		switch (ekind) {
		case IClasspathEntry.CPE_SOURCE:
			return "SRC";
		case IClasspathEntry.CPE_LIBRARY:
			return "LIB";
		case IClasspathEntry.CPE_PROJECT:
			return "PRJ";
		case IClasspathEntry.CPE_VARIABLE:
			return "VAR";
		case IClasspathEntry.CPE_CONTAINER:
			return "CON";
		default:
			return ""+ekind;
		}
	}
	
	private void jarDump(JarFile jarFile) {
		Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements()) {
			JarEntry e = entries.nextElement();
			System.out.println(e.getName());
		}
	}

	
	
//	/**
//	 * Load the {@link ConfigMetadataRepository} with the metadata defined by
//	 * the specified {@code resources}. If the same config metadata items is
//	 * held within different resources, the first that is loaded is kept.
//	 */
//	public ConfigMetadataRepository load(Collection<IFile> resources) throws IOException {
//		Assert.notNull(resources, "Resources must not be null");
//		if (resources.size() == 1) {
//			return load(resources.iterator().next());
//		}
//
//		SimpleConfigMetadataRepository repository = new SimpleConfigMetadataRepository();
//		for (IResource resource : resources) {
//			ConfigMetadataRepository repo = load(resource);
//			repository.include(repo);
//		}
//		return repository;
//	}
//
//	private ConfigMetadataRepository load(Resource resource) throws IOException {
//		InputStream in = resource.getInputStream();
//		try {
//			return mapper.readRepository(in);
//		}
//		catch (IOException e) {
//			throw new IllegalStateException("Failed to read config metadata from '" + resource + "'", e);
//		}
//		catch (JSONException e) {
//			throw new IllegalStateException("Invalid config metadata document defined at '" + resource + "'", e);
//		}
//
//		finally {
//			in.close();
//		}
//	}

}
