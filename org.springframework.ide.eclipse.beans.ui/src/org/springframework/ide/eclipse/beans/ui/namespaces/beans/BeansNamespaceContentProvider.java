/*
 * Copyright 2002-2006 the original author or authors.
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

package org.springframework.ide.eclipse.beans.ui.namespaces.beans;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.springframework.ide.eclipse.beans.core.BeansCorePlugin;
import org.springframework.ide.eclipse.beans.core.model.IBeanConstructorArgument;
import org.springframework.ide.eclipse.beans.core.model.IBeanProperty;
import org.springframework.ide.eclipse.core.model.IModelElement;

/**
 * This class is a content provider which knows about the beans core model's
 * {@link IModelElement elements} in the namespace
 * <code>"http://www.springframework.org/schema/beans"</code>.
 * 
 * @author Torsten Juergeleit
 */
public class BeansNamespaceContentProvider implements ITreeContentProvider {

	public Object[] getElements(Object inputElement) {
		return getChildren(BeansCorePlugin.getModel());
	}

	public boolean hasChildren(Object element) {
		if (element instanceof IModelElement) {
			return !(element instanceof IBeanProperty
					|| element instanceof IBeanConstructorArgument);
		}
		return false;
	}

	public Object[] getChildren(Object parentElement) {
		return ((IModelElement) parentElement).getElementChildren();
	}

	public Object getParent(Object element) {
		if (element instanceof IModelElement) {
			return ((IModelElement) element).getElementParent();
		}
		return null;
	}

	public void dispose() {
	}

	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
	}
}
