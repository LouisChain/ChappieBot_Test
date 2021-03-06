package com.launcher.chappiebot.ui.newsFeeddetail

import android.os.Bundle
import android.view.Surface
import android.view.View
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import com.google.android.material.snackbar.Snackbar
import com.launcher.chappiebot.R
import com.launcher.chappiebot.animation.SimpleTransitionListener
import com.launcher.chappiebot.databinding.FragmentNewsfeedDetailBinding

import com.launcher.chappiebot.extensions.observe
import com.launcher.chappiebot.extensions.onSurfaceAvailable
import com.launcher.chappiebot.ui.base.BaseFragment
import com.launcher.chappiebot.ui.widget.ToggleSoundButton
import kotlinx.android.synthetic.main.fragment_newsfeed_detail.*
import javax.inject.Inject

class NewsFeedDetailFragment : BaseFragment<FragmentNewsfeedDetailBinding, NewsFeedDetailViewModel>() {
    private var isLoading = false
    private var loadingFinishedPending = false
    override val layoutRes = R.layout.fragment_newsfeed_detail
    @Inject
    override lateinit var viewModel: NewsFeedDetailViewModel
    @Inject
    lateinit var enterAndExitTransition: Transition

    private fun onStateChanged(state: NewsFeedDetailViewModel.State) {
        when (state) {
            is NewsFeedDetailViewModel.InitState -> transitionToStart()
            is NewsFeedDetailViewModel.LoadingState -> transitionToLoading()
            is NewsFeedDetailViewModel.LoadingFinishedState -> transitionToLoadingFinished()
            is NewsFeedDetailViewModel.PlayingState -> transitionToPlaying(state.initial)
            is NewsFeedDetailViewModel.PausedState -> transitionToPaused()
            is NewsFeedDetailViewModel.CompletedState -> transitionToCompleted()
            is NewsFeedDetailViewModel.ErrorState -> transitionToError()
        }
    }

    private fun toggleOverlay(visible: Boolean) {
        if (visible) {
            videoOverlay?.transitionToEnd()
        } else {
            videoOverlay?.transitionToStart()
        }
    }

    private fun setTitle(title: String) {
        collapsingToolbar?.title = title
    }

    private fun toggleSoundButton(soundOn: Boolean) {
        fullScreenVolume?.soundState = if (soundOn) {
            ToggleSoundButton.SoundState.ON
        } else {
            ToggleSoundButton.SoundState.OFF
        }
    }

    private fun transitionToError() {
        isLoading = false
        transitionToLoadingFinished()
        coordinator?.let { layout ->
            Snackbar.make(layout, R.string.video_detail_video_failed_to_load, Snackbar.LENGTH_INDEFINITE)
                    .apply {
                        setAction(R.string.video_detail_dismiss_snackbar) { dismiss() }
                        show()
                    }
        }
    }

    private fun transitionToStart() {
        mainAction?.runPauseToPlay()
        fullScreenPlay?.runPauseToPlay()
        videoContainer?.transitionToStart()
        actionContainer?.transitionToStart()
    }

    private fun transitionToLoadingFinished() {
        if (isLoading) {
            loadingFinishedPending = true
            return
        }
        loaderContainer?.apply {
            setTransitionListener(
                    SimpleTransitionListener(
                            onComplete = { _, _ -> viewModel.onLoadingAnimationFinished() }
                    )
            )
            arc?.apply {
                registerLoadingCompleteListener {
                    setTransition(R.id.end, R.id.back)
                    transitionToEnd()
                }
                loading = false
            }
        }
    }

    private fun transitionToPaused() {
        mainAction?.runPauseToPlay()
        fullScreenPlay?.runPauseToPlay()
    }

    private fun transitionToCompleted() {
        mainAction?.markCompleted()
        fullScreenPlay?.markCompleted()
    }

    private fun startLoaderTransition() {
        loaderContainer?.apply {
            setTransition(R.id.start, R.id.end)
            setTransitionListener(
                    SimpleTransitionListener(
                            onComplete = { _, _ ->
                                isLoading = false
                                arc?.loading = true
                                if (loadingFinishedPending) {
                                    transitionToLoadingFinished()
                                }
                            }
                    )
            )
            transitionToEnd()
        }
    }

    private fun transitionToLoading() {
        isLoading = true
        actionContainer?.apply {
            loadLayoutDescription(R.xml.video_detail_fab_fire_scene)
            setTransition(R.id.start, R.id.end)
            var hasStartedLoaderTransition = false
            val actionTransitionListener = SimpleTransitionListener(
                    onChange = { _, _, _, progress ->
                        if (progress >= 0.5 && !hasStartedLoaderTransition) {
                            startLoaderTransition()
                            hasStartedLoaderTransition = true
                        }
                    },
                    onComplete = { _, _ ->
                        if (!hasStartedLoaderTransition) {
                            startLoaderTransition()
                            hasStartedLoaderTransition = true
                        }
                    }
            )
            setTransitionListener(actionTransitionListener)
            transitionToEnd()
        }
    }

    private fun toggleFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            actionContainer?.apply {
                loadLayoutDescription(R.xml.video_detail_fab_container_scene)
                setTransition(R.id.end, R.id.fullScreen)
                transitionToEnd()
            }
            contentContainer?.apply {
                setTransition(R.id.start, R.id.end)
                transitionToEnd()
            }
            videoContainer?.apply {
                setTransition(R.id.videoPlaying, R.id.end)
                setTransitionListener(SimpleTransitionListener(
                        onComplete = { _, _ -> viewModel.onLandscapeTransitionFinished() }))
                transitionToEnd()
            }

            landscapeVideoRoot?.transitionToEnd()
        } else {
            actionContainer?.apply {
                setTransition(R.id.end, R.id.start)
                transitionToEnd()
            }
            contentContainer?.apply {
                setTransition(R.id.end, R.id.start)
                transitionToEnd()
            }
        }
    }

    private fun transitionToPlaying(initial: Boolean) {
        if (initial) {
            actionContainer?.apply {
                loadLayoutDescription(R.xml.video_detail_fab_container_scene)
                setTransition(R.id.start, R.id.end)
                transitionToEnd()
            }
            videoContainer?.transitionToState(R.id.videoPlaying)
        }

        mainAction?.runPlayToPause()
        fullScreenPlay?.runPlayToPause()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val sharedElementTransition = TransitionInflater.from(requireContext())
                .inflateTransition(R.transition.newsfeed_detail_shared_element_transition)

        if (::enterAndExitTransition.isInitialized) {
            enterTransition = enterAndExitTransition
            exitTransition = enterAndExitTransition
        }

        sharedElementReturnTransition = sharedElementTransition
        sharedElementEnterTransition = sharedElementTransition
    }

    override fun onBindingCreated(binding: FragmentNewsfeedDetailBinding) {
        val owner = this
        viewModel.apply {
            overlayVisible.observe(owner, ::toggleOverlay)
            fullscreen.observe(owner, ::toggleFullscreen)
            soundOn.observe(owner, ::toggleSoundButton)
            state.observe(owner, ::onStateChanged)
            title.observe(owner, ::setTitle)
            binding.viewModel = this
            binding.videoView.onSurfaceAvailable { surface ->
                newsFeedDetailPlayer.attachSurface(Surface(surface))
            }
        }
    }
}