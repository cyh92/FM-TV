package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHistoryBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class HistoryActivity extends BaseActivity implements HistoryPresenter.OnClickListener {

    private ActivityHistoryBinding mBinding;
    private ArrayObjectAdapter mHistoryAdapter;
    private HistoryPresenter mPresenter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, HistoryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        setRecyclerView();
        getHistory();
    }

    @Override
    protected void initEvent() {
        mBinding.delete.setOnClickListener(this::onDelete);
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mHistoryAdapter = new ArrayObjectAdapter(mPresenter = new HistoryPresenter(this));
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mHistoryAdapter));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(this, Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void getHistory() {
        List<History> items = History.get();
        mHistoryAdapter.setItems(items, null);
        App.post(() -> {
            mBinding.delete.setVisibility(items.size() == 0 ? View.GONE : View.VISIBLE);
            mBinding.delete.setFocusable(true);
        }, 500);
        mBinding.recycler.requestFocus();
    }

    private void onDelete(View view) {
        if (mPresenter.isDelete()) {
         new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_delete_record).setMessage(R.string.dialog_delete_history).setNegativeButton(R.string.dialog_negative, null).setBackground(ContextCompat.getDrawable(this,R.drawable.selector_text)).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                History.delete(VodConfig.getCid());
                mHistoryAdapter.clear();
                mPresenter.setDelete(false);
                mBinding.delete.setVisibility(View.GONE);
            }).setBackground(ContextCompat.getDrawable(this,R.drawable.selector_text)).show();

        } else if (mHistoryAdapter.size() > 0) {
            mPresenter.setDelete(true);
            mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
        } else {
            mBinding.delete.setVisibility(View.GONE);
        }
    }

    @Override
    public void onItemClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onItemDelete(History item) {
        mBinding.delete.setFocusable(false);
        item.delete();
        mHistoryAdapter.remove(item);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
        if (mHistoryAdapter.size() == 0) {
            mPresenter.setDelete(false);
            mBinding.delete.setVisibility(View.GONE);
        }
        App.post(() -> {
            mBinding.delete.setFocusable(true);
        }, 300);
    }

    @Override
    public boolean onLongClick() {
        mPresenter.setDelete(true);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
//        super.onRefreshEvent(event);
        switch (event.getType()) {
            case HISTORY:
            case SIZE:
                getHistory();
                break;
        }
    }
    @Override
    protected void onBackInvoked() {
        if (mPresenter.isDelete()) {
            mPresenter.setDelete(false);
            mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
        } else {
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RefreshEvent.history();
    }
}