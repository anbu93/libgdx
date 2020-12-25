/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.assets.loaders;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

/** {@link AssetLoader} to load {@link FileHandle} instances.
 * used by gwt backend for load {@link FileHandle files} which not preloaded */
public class FileHandleLoader extends AsynchronousAssetLoader<FileHandle, FileHandleLoader.FileHandleParameters> {
    private ObjectMap<String, FileHandle> fileHandles = new ObjectMap<String, FileHandle>();

    public FileHandleLoader(FileHandleResolver resolver) {
        super(resolver);
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, FileHandleParameters parameter) {
        fileHandles.put(fileName, file);
    }

    @Override
    public FileHandle loadSync(AssetManager manager, String fileName, FileHandle file, FileHandleParameters parameter) {
        FileHandle resultFile = fileHandles.get(fileName);
        fileHandles.remove(fileName);
        return resultFile;
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, FileHandleParameters parameter) {
        return null;
    }

    static public class FileHandleParameters extends AssetLoaderParameters<FileHandle> {
        public FileHandleParameters() {}
    }
}