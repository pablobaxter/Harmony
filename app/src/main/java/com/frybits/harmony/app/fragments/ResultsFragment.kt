package com.frybits.harmony.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.frybits.harmony.app.R
import com.frybits.harmony.app.databinding.FragmentResultsBinding
import com.frybits.harmony.app.repository.TestRepository
import com.frybits.harmony.app.view.ResultsListAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@AndroidEntryPoint
class ResultsFragment : Fragment() {

    @Inject
    lateinit var testRepository: TestRepository

    private val resultsListAdapter = ResultsListAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentResultsBinding.inflate(inflater, container, false)

        with(binding) {
            resultsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            resultsRecyclerView.adapter = resultsListAdapter

            lifecycleScope.launch {
                resultsListAdapter.submitList(testRepository.getAllTestSuites())
            }
        }

        return binding.root
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_clear, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clearItem -> {
                lifecycleScope.launch {
                    testRepository.clearAllItems()
                    resultsListAdapter.submitList(testRepository.getAllTestSuites())
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}