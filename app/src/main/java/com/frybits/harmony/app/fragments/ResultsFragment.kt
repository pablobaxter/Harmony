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