package com.frybits.harmony.app.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.frybits.harmony.app.databinding.ViewResultBinding
import com.frybits.harmony.app.test.TestData
import com.frybits.harmony.app.test.TestSource
import com.frybits.harmony.app.test.TestSuiteData
import com.frybits.harmony.app.test.TestType

/*
 *  Copyright 2021 Pablo Baxter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 * https://github.com/pablobaxter/Harmony
 */

private val TEST_SUITE_DIFF_CALLBACK = object : DiffUtil.ItemCallback<TestSuiteData>() {
    override fun areItemsTheSame(oldItem: TestSuiteData, newItem: TestSuiteData): Boolean {
        return oldItem.testSuite.id == newItem.testSuite.id
    }

    override fun areContentsTheSame(oldItem: TestSuiteData, newItem: TestSuiteData): Boolean {
        return oldItem.testEntityWithDataList == newItem.testEntityWithDataList
    }
}

class ResultsListAdapter : ListAdapter<TestSuiteData, ResultsViewHolder>(TEST_SUITE_DIFF_CALLBACK){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultsViewHolder {
        val binderView = ViewResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ResultsViewHolder(binderView)
    }

    override fun onBindViewHolder(holder: ResultsViewHolder, position: Int) {
        holder.bindTo(getItem(position))
    }
}

class ResultsViewHolder(private val binderView: ViewResultBinding) : RecyclerView.ViewHolder(binderView.root) {

    fun bindTo(testSuiteData: TestSuiteData) {
        val sharedPrefsTests = testSuiteData.getTestData(TestSource.SHARED_PREFS)
        val harmonyTests = testSuiteData.getTestData(TestSource.HARMONY)
        val mmkvTests = testSuiteData.getTestData(TestSource.MMKV)
        val trayTests = testSuiteData.getTestData(TestSource.TRAY)
        with(binderView) {
            var max = maxOf(sharedPrefsTests.filter { it.testDataType == TestType.READ }.count(), harmonyTests.filter { it.testDataType == TestType.READ }.count())
            max = maxOf(max, mmkvTests.filter { it.testDataType == TestType.READ }.count())
            max = maxOf(max, trayTests.filter { it.testDataType == TestType.READ }.count())
            numTestsTextView.text = "Number of runs: $max"

            itemsStoredTextView.text = "Items stored per run: ${testSuiteData.testEntityWithDataList.first().entity.numIterations}"

            sharedPrefsReadTextView.text = sharedPrefsTests.evaluateTime(TestType.READ)
            sharedPrefsWriteTextView.text = sharedPrefsTests.evaluateTime(TestType.WRITE)
            sharedPrefsIpcTextView.text = sharedPrefsTests.evaluateTime(TestType.IPC)

            harmonyReadTextView.text = harmonyTests.evaluateTime(TestType.READ)
            harmonyWriteTextView.text = harmonyTests.evaluateTime(TestType.WRITE)
            harmonyIpcTextView.text = harmonyTests.evaluateTime(TestType.IPC)

            mmkvReadTextView.text = mmkvTests.evaluateTime(TestType.READ)
            mmkvWriteTextView.text = mmkvTests.evaluateTime(TestType.WRITE)
            mmkvIpcTextView.text = mmkvTests.evaluateTime(TestType.IPC)

            trayReadTextView.text = trayTests.evaluateTime(TestType.READ)
            trayWriteTextView.text = trayTests.evaluateTime(TestType.WRITE)
            trayIpcTextView.text = trayTests.evaluateTime(TestType.IPC)

            isAsyncCheckBox.isChecked = testSuiteData.testEntityWithDataList.first().entity.isAsync
            isEncryptedCheckBox.isChecked = testSuiteData.testEntityWithDataList.first().entity.isEncrypted
        }
    }

    private fun TestSuiteData.getTestData(source: String): List<TestData> {
        return testEntityWithDataList.filter { it.entity.source == source }.flatMap { it.testDataList }
    }

    private fun List<TestData>.evaluateTime(type: String): String {
        var curr = filter { it.testDataType == type }.flatMap { it.results.asIterable() }.filter { it >= 0 }.average()
        if (curr.isNaN()) return "N/A"
        var measure = "ns"
        if (curr >= 1000) {
            measure = "ms"
            curr /= 1_000_000
        }

        if (curr >= 1000) {
            measure = "s"
            curr /= 1_000

            if (curr >= 10) {
                return "Too long"
            }
        }
        return "%.3f$measure".format(curr)
    }
}
