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

package com.badlogic.gdx.assets;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.utils.ObjectSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** {@link AssetManager} with loading multiple tasks per update */
public class ParallelAssetManager extends AssetManager {
    public static final int INFINITY_TASKS_UPDATE = -1;
    private int tasksCount = INFINITY_TASKS_UPDATE;
    private List<AssetLoadingTask> tasksTmp = new ArrayList<AssetLoadingTask>(); // concurrent modification exception safe
    private List<AssetLoadingTask> removeTasks = new ArrayList<AssetLoadingTask>(); // concurrent modification exception safe
    private List<AssetLoadingTask> delayTasks = new ArrayList<AssetLoadingTask>(); // for update dependencies have tasks delay
    private ObjectSet<String> assetsSet = new ObjectSet<String>(); // for duplicate tasks check
    private boolean isDisableRemoveDuplicates = false; // crutch

    public ParallelAssetManager() {}

    public ParallelAssetManager(int tasksCount) {
        this.tasksCount = tasksCount;
    }

    public ParallelAssetManager(FileHandleResolver resolver) {
        super(resolver);
    }

    public ParallelAssetManager(FileHandleResolver resolver, int tasksCount) {
        super(resolver);
        this.tasksCount = tasksCount;
    }

    public ParallelAssetManager(FileHandleResolver resolver, boolean defaultLoaders) {
        super(resolver, defaultLoaders);
    }

    public ParallelAssetManager(FileHandleResolver resolver, boolean defaultLoaders, int tasksCount) {
        super(resolver, defaultLoaders);
        this.tasksCount = tasksCount;
    }

    public int getTasksCount() {
        return tasksCount;
    }

    public void setTasksCount(int tasksCount) {
        this.tasksCount = tasksCount;
    }

    @Override
    public synchronized boolean update() {
        try {
            if (tasks.size() == 0) {
                isDisableRemoveDuplicates = true; // disable check duplicate tasks on add new task (crutch)
                // apply delay tasks if exists
                if (delayTasks.size() > 0) {
                    applyDelayTasks();
                } else {
                    // loop until we have a new task ready to be processed
                    while (loadQueue.size != 0 && (tasksCount == INFINITY_TASKS_UPDATE || tasks.size() < tasksCount))
                        nextTask();
                }
                isDisableRemoveDuplicates = false;
                removeDuplicateTasks();
                // have we not found a task? We are done!
                if (tasks.size() == 0) return true;
            }
            return updateTask() && loadQueue.size == 0 && tasks.size() == 0;
        } catch (Throwable t) {
            handleTaskError(t);
            return loadQueue.size == 0 && tasks.size() == 0;
        }
    }

    @Override
    void addTask(AssetDescriptor assetDesc) {
        super.addTask(assetDesc);
        removeDuplicateTasks();
    }

    //TODO optimise
    private void removeDuplicateTasks() {
        if (isDisableRemoveDuplicates) return;
        Iterator<AssetLoadingTask> iterator = tasks.iterator();
        while(iterator.hasNext()) {
            AssetLoadingTask task = iterator.next();
            if (assetsSet.contains(task.assetDesc.fileName)) {
                iterator.remove();
            } else {
                assetsSet.add(task.assetDesc.fileName);
            }
        }
        assetsSet.clear();
    }

    boolean completeTotal, complete, dependenciesLoaded;  // optimisation method local fields
    @Override
    boolean updateTask() {
        if (tasks.size() == 0) return true;
        completeTotal = true;
        tasksTmp.addAll(tasks); // concurrent modification exception safe
        for(AssetLoadingTask task : tasksTmp) {
            try {
                dependenciesLoaded = task.dependenciesLoaded;
                complete = task.cancel || task.update();
                if (task.hasDependencies()) { // delay, load dependencies of task first
                    if (dependenciesLoaded != task.dependenciesLoaded) {
                        delayTasks.add(task);
                        removeTasks.add(task);
                    }
                }
            } catch (RuntimeException ex) {
                task.cancel = true;
                taskFailed(task.assetDesc, ex);
            }
            if (complete) {
                loaded++;
                peakTasks--;
                if (peakTasks < 0) peakTasks = 0;
                removeTasks.add(task);
                if (!task.cancel) {
                    addAsset(task.assetDesc.fileName, task.assetDesc.type, task.asset);
                    // otherwise, if a listener was found in the parameter invoke it
                    if (task.assetDesc.params != null && task.assetDesc.params.loadedCallback != null)
                        task.assetDesc.params.loadedCallback.finishedLoading(this, task.assetDesc.fileName, task.assetDesc.type);
                }
            } else {
                completeTotal = false;
            }
        }
        if (removeTasks.size() > 0) {
            for(AssetLoadingTask task : removeTasks)
                tasks.remove(task);
            removeTasks.clear();
        }
        tasksTmp.clear();
        if (tasks.size() == 0 && !delayTasks.isEmpty()) {
            applyDelayTasks();
            completeTotal = false;
        }
        return completeTotal;
    }

    private void applyDelayTasks() {
        tasks.addAll(delayTasks);
        delayTasks.clear();
    }
}
