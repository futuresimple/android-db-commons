package com.getbase.android.db.provider;

import com.getbase.android.db.common.QueryData;
import com.getbase.android.db.cursors.FluentCursor;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

public class Query extends ProviderAction<FluentCursor> {

  private final Selection selection = new Selection();
  private final Projection projection = new Projection();
  private String orderBy;

  Query(Uri uri) {
    super(uri);
  }

  public Query projection(String... projection) {
    this.projection.append(projection);
    return this;
  }

  public Query where(String selection, Object... selectionArgs) {
    this.selection.append(selection, selectionArgs);
    return this;
  }

  public Query orderBy(String orderBy) {
    this.orderBy = orderBy;
    return this;
  }

  public QueryData getQueryData() {
    return new QueryData(getUri(),
        projection.getProjection(),
        selection.getSelection(),
        selection.getSelectionArgs(),
        orderBy
    );
  }

  @Override
  public FluentCursor perform(ContentResolver contentResolver) {
    final Cursor queryResult = contentResolver.query(getUri(),
        projection.getProjection(),
        selection.getSelection(),
        selection.getSelectionArgs(),
        orderBy
    );
    return new FluentCursor(queryResult);
  }
}