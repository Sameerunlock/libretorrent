package com.torrentbox.app.ui.home;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.torrentbox.app.R;
import com.torrentbox.app.core.model.data.TorrentListState;
import com.torrentbox.app.databinding.FragmentHiddenBinding;

import com.torrentbox.app.ui.utils.HiddenTorrentStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HiddenFragment extends Fragment {

    private static final String SELECTION_TRACKER_ID = "hidden_selection_tracker";
    private static final String KEY_DELETE_HIDDEN_DIALOG =
            "hidden_delete_torrent_dialog";

    private FragmentHiddenBinding binding;
    private TorrentListAdapter adapter;
    private HomeViewModel viewModel;
    private SelectionTracker<TorrentListItem> selectionTracker;

    private final CompositeDisposable disposables = new CompositeDisposable();

    // 🔍 Search support
    private final List<TorrentListItem> fullHiddenList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        binding = FragmentHiddenBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        if (!HiddenTorrentStore.isUnlocked(requireContext())) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }

        adapter = new TorrentListAdapter(new TorrentListAdapter.ClickListener() {
            @Override
            public void onItemClicked(@NonNull TorrentListItem item) {
                openDetails(item);
            }

            @Override
            public void onItemPauseClicked(@NonNull TorrentListItem item) {
                // not used in hidden
            }
        });

        binding.hiddenTorrentList.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        binding.hiddenTorrentList.setAdapter(adapter);
        binding.hiddenTorrentList.setEmptyView(binding.emptyHiddenList);

        viewModel = new ViewModelProvider(requireActivity())
                .get(HomeViewModel.class);

        setupSelectionTracker();
        setupContextualMenu();
        setupSearch();
        observeHiddenTorrents();
        setupDeleteDialogListener();
    }

    // =====================================================
    // Selection
    // =====================================================

    private void setupSelectionTracker() {

        selectionTracker = new SelectionTracker.Builder<>(
                SELECTION_TRACKER_ID,
                binding.hiddenTorrentList,
                new TorrentListAdapter.KeyProvider(adapter),
                new TorrentListAdapter.ItemLookup(binding.hiddenTorrentList),
                StorageStrategy.createParcelableStorage(TorrentListItem.class)
        )
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();

        adapter.setSelectionTracker(selectionTracker);

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<>() {
            @Override
            public void onSelectionChanged() {
                if (selectionTracker.hasSelection()) {
                    binding.hiddenContextualBar.setVisibility(View.VISIBLE);
                    binding.hiddenContextualBar.setTitle(
                            selectionTracker.getSelection().size() + " selected"
                    );
                } else {
                    binding.hiddenContextualBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void selectAllHidden() {
        if (adapter.getItemCount() == 0) return;
        selectionTracker.clearSelection();
        selectionTracker.startRange(0);
        selectionTracker.extendRange(adapter.getItemCount() - 1);
    }

    // =====================================================
    // Contextual Menu
    // =====================================================

    private void setupContextualMenu() {

        binding.hiddenContextualBar.setOnMenuItemClickListener(item -> {

            int id = item.getItemId();

            if (id == R.id.action_select_all) {
                selectAllHidden();
                return true;
            }

            if (id == R.id.action_unhide) {
                unhideSelected();
                return true;
            }

            if (id == R.id.action_delete) {
                openDeleteDialog();
                return true;
            }

            return false;
        });
    }

    // =====================================================
    // Search (Material SearchBar + SearchView)
    // =====================================================

    private void setupSearch() {

        binding.hiddenSearchBar.setOnClickListener(v ->
                binding.hiddenSearchView.show()
        );

        binding.hiddenSearchView.getEditText()
                .addTextChangedListener(new TextWatcher() {

                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        filterHidden(s.toString());
                    }

                    @Override public void afterTextChanged(Editable s) {}
                });

        binding.hiddenSearchView.addTransitionListener(
                (searchView, prev, state) -> {
                    if (state ==
                            com.google.android.material.search.SearchView.TransitionState.HIDDEN) {
                        filterHidden("");
                    }
                }
        );
    }

    private void filterHidden(String query) {

        if (query == null || query.trim().isEmpty()) {
            adapter.submitList(new ArrayList<>(fullHiddenList));
            return;
        }

        String lower = query.toLowerCase();
        List<TorrentListItem> filtered = new ArrayList<>();

        for (TorrentListItem item : fullHiddenList) {
            if (item.name.toLowerCase().contains(lower)) {
                filtered.add(item);
            }
        }

        adapter.submitList(filtered);
    }

    // =====================================================
    // Data
    // =====================================================

    private void observeHiddenTorrents() {

        disposables.add(
                viewModel.observeAllTorrentsInfo()
                        .subscribeOn(Schedulers.io())
                        .filter(state -> state instanceof TorrentListState.Loaded)
                        .map(state -> (TorrentListState.Loaded) state)
                        .flatMapSingle(loaded ->
                                Flowable.fromIterable(loaded.list())
                                        .filter(info ->
                                                HiddenTorrentStore.isHidden(
                                                        requireContext(),
                                                        info.torrentId
                                                )
                                        )
                                        .map(TorrentListItem::new)
                                        .sorted(viewModel.getSorting())
                                        .toList()
                        )
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(list -> {
                            fullHiddenList.clear();
                            fullHiddenList.addAll(list);
                            adapter.submitList(list);
                        }, e -> Log.e("HiddenFragment", "Error", e))
        );
    }

    // =====================================================
    // Actions
    // =====================================================

    private void unhideSelected() {

        if (!selectionTracker.hasSelection()) return;

        Set<String> ids = new HashSet<>();
        for (TorrentListItem item : selectionTracker.getSelection()) {
            ids.add(item.torrentId);
        }

        HiddenTorrentStore.unhide(requireContext(), ids);
        selectionTracker.clearSelection();
        binding.hiddenContextualBar.setVisibility(View.GONE);
    }

    // ---------- DELETE (Home-style dialog) ----------

    private void openDeleteDialog() {

        int count = selectionTracker.getSelection().size();
        if (count == 0) return;

        var action = HiddenFragmentDirections
                .actionDeleteTorrentDialog(count, KEY_DELETE_HIDDEN_DIALOG);

        NavHostFragment.findNavController(this).navigate(action);
    }

    private void setupDeleteDialogListener() {

        getParentFragmentManager().setFragmentResultListener(
                KEY_DELETE_HIDDEN_DIALOG,
                this,
                (key, result) -> {

                    DeleteTorrentDialog.Result res =
                            (DeleteTorrentDialog.Result)
                                    result.getSerializable(
                                            DeleteTorrentDialog.KEY_RESULT_VALUE);

                    if (res == null || res == DeleteTorrentDialog.Result.CANCEL) return;

                    boolean withFiles =
                            res == DeleteTorrentDialog.Result.DELETE_WITH_FILES;

                    performDelete(withFiles);
                }
        );
    }

    private void performDelete(boolean withFiles) {

        Set<String> hiddenIds = new HashSet<>();
        List<String> deleteIds = new ArrayList<>();

        for (TorrentListItem item : selectionTracker.getSelection()) {
            hiddenIds.add(item.torrentId);
            deleteIds.add(item.torrentId);
        }

        HiddenTorrentStore.unhide(requireContext(), hiddenIds);
        viewModel.deleteTorrents(deleteIds, withFiles);

        selectionTracker.clearSelection();
        binding.hiddenContextualBar.setVisibility(View.GONE);
    }

    private void openDetails(TorrentListItem item) {
        NavHostFragment.findNavController(this)
                .navigate(
                        HiddenFragmentDirections
                                .actionHiddenToTorrentDetails(item.torrentId)
                );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        disposables.clear();
    }
}
