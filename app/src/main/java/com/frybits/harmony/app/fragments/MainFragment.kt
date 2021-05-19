package com.frybits.harmony.app.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.frybits.harmony.app.databinding.FragmentMainBinding
import com.frybits.harmony.app.repository.TestRepository
import com.frybits.harmony.app.test.HarmonyTestRunner
import com.frybits.harmony.app.test.LogEvent
import com.frybits.harmony.app.test.MMKVTestRunner
import com.frybits.harmony.app.test.TestRunner
import com.frybits.harmony.app.test.TestSuiteRunner
import com.frybits.harmony.app.test.TraySharedPrefsRunner
import com.frybits.harmony.app.test.VanillaSharedPreferencesTestRunner
import com.frybits.harmony.app.view.LogListAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PREFS_NAME = "harmonyTestPrefs"

@AndroidEntryPoint
class MainFragment : Fragment() {

    @Inject
    lateinit var testRepository: TestRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentMainBinding.inflate(inflater, container, false)

        with(binding) {
            val logListAdapter = LogListAdapter()
            runningLogsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            runningLogsRecyclerView.adapter = logListAdapter

            startTestButton.setOnClickListener {

                val runnerList = arrayListOf<TestRunner>()
                if (vanillaSharedPrefsSwitch.isChecked) {
                    runnerList.add(
                        VanillaSharedPreferencesTestRunner(
                            context = requireContext(),
                            prefsName = PREFS_NAME,
                            iterations = requireNotNull(
                                testIterationEditText.editText?.text?.toString()?.toInt()
                            ),
                            isAsync = testApplySwitch.isChecked,
                            isEncrypted = useEncryptionSwitch.isChecked
                        )
                    )
                }

                if (harmonySwitch.isChecked) {
                    runnerList.add(
                        HarmonyTestRunner(
                            context = requireContext(),
                            prefsName = PREFS_NAME,
                            iterations = requireNotNull(
                                testIterationEditText.editText?.text?.toString()?.toInt()
                            ),
                            isAsync = testApplySwitch.isChecked,
                            isEncrypted = useEncryptionSwitch.isChecked
                        )
                    )
                }

                if (mmkvSwitch.isChecked) {
                    runnerList.add(
                        MMKVTestRunner(
                            context = requireContext(),
                            prefsName = PREFS_NAME,
                            iterations = requireNotNull(
                                testIterationEditText.editText?.text?.toString()?.toInt()
                            ),
                            isAsync = testApplySwitch.isChecked,
                            isEncrypted = useEncryptionSwitch.isChecked
                        )
                    )
                }

                if (traySwitch.isChecked) {
                    runnerList.add(
                        TraySharedPrefsRunner(
                            context = requireContext(),
                            prefsName = PREFS_NAME,
                            iterations = requireNotNull(
                                testIterationEditText.editText?.text?.toString()?.toInt()
                            ),
                            isAsync = testApplySwitch.isChecked,
                            isEncrypted = useEncryptionSwitch.isChecked
                        )
                    )
                }

                val testSuiteRunner = TestSuiteRunner(
                    testRunners = runnerList,
                    testRunnerIterations = requireNotNull(
                        testNumberEditText.editText?.text?.toString()?.toInt()
                    )
                )

                val logList = arrayListOf<LogEvent>()
                val job = testSuiteRunner.logFlow
                    .onEach {
                        logList.add(it)
                        logListAdapter.submitList(logList.toList())
                        runningLogsRecyclerView.smoothScrollToPosition(logList.size - 1)
                    }
                    .launchIn(lifecycleScope)
                lifecycleScope.launch {
                    try {
                        enableUi(false)
                        testSuiteRunner.runTests()
                        testRepository.storeTestSuite(testSuiteRunner.getResults())
                    } finally {
                        enableUi(true)
                        job.cancel()
                    }
                }
            }

            viewResultsButton.setOnClickListener {
                findNavController().navigate(MainFragmentDirections.actionMainFragmentToResultsFragment())
            }
        }

        return binding.root
    }

    private fun FragmentMainBinding.enableUi(isEnabled: Boolean) {
        testNumberEditText.isEnabled = isEnabled
        testIterationEditText.isEnabled = isEnabled
        testApplySwitch.isEnabled = isEnabled
        useEncryptionSwitch.isEnabled = isEnabled
        vanillaSharedPrefsSwitch.isEnabled = isEnabled
        harmonySwitch.isEnabled = isEnabled
        mmkvSwitch.isEnabled = isEnabled
        traySwitch.isEnabled = isEnabled
        startTestButton.isEnabled = isEnabled
        viewResultsButton.isEnabled = isEnabled
    }
}