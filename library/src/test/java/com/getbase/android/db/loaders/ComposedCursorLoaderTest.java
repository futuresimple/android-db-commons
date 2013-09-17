package com.getbase.android.db.loaders;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.getbase.android.db.CustomRobolectricTestRunner;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;

import android.content.ContentProvider;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.support.v4.content.Loader;

import java.util.List;

@RunWith(CustomRobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ComposedCursorLoaderTest {

  public static final String FAKE_AUTHORITY = "com.getbase.android.database";
  private static final Uri TEST_URI = Uri.parse(String.format("content://%s/people", FAKE_AUTHORITY));

  @Mock
  private ContentProvider providerMock;

  private MatrixCursor cursor;

  private Function<Cursor, String> genericToStringFunction = new Function<Cursor, String>() {
    @Override
    public String apply(Cursor cursor) {
      return cursor.getString(0);
    }
  };

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    ShadowContentResolver.registerProvider(FAKE_AUTHORITY, providerMock);
    cursor = new MatrixCursor(new String[] { "name" });
    cursor.addRow(new Object[] { "my_name" });
    cursor.addRow(new Object[] { "my_second_name" });
    makeProviderReturn(cursor);
    Robolectric.getBackgroundScheduler().pause();
  }

  private void makeProviderReturn(Cursor cursor) {
    when(providerMock.query(eq(TEST_URI), (String[]) isNull(),
        (String) isNull(), (String[]) isNull(), (String) isNull()))
        .thenReturn(cursor);
  }

  @Test
  public void shouldApplyTransformFunctionInDoInBackground() throws Exception {
    final Loader<List<String>> loader = CursorLoaderBuilder.forUri(TEST_URI)
        .transform(genericToStringFunction)
        .build(Robolectric.application);
    loader.startLoading();
    final Loader.OnLoadCompleteListener<List<String>> listenerMock = mock(Loader.OnLoadCompleteListener.class);
    loader.registerListener(0, listenerMock);
    Robolectric.getBackgroundScheduler().runOneTask();
    verify(listenerMock).onLoadComplete(same(loader), eq(Lists.newArrayList("my_name", "my_second_name")));
  }

  @Test
  public void shouldNotCloseJustReturnedCursor() throws Exception {
    final Loader<List<String>> loader = CursorLoaderBuilder.forUri(TEST_URI)
        .transform(genericToStringFunction)
        .build(Robolectric.application);
    loader.startLoading();
    Robolectric.getBackgroundScheduler().runOneTask();
    assertThat(cursor.isClosed()).isFalse();
  }

  @Test
  public void shouldCloseOldCursorIfNewOneAppears() throws Exception {
    final Loader<List<String>> loader = CursorLoaderBuilder.forUri(TEST_URI)
        .transform(genericToStringFunction)
        .build(Robolectric.application);
    loader.startLoading();
    Robolectric.getBackgroundScheduler().runOneTask();
    final MatrixCursor secondCursor = new MatrixCursor(cursor.getColumnNames());
    loader.reset();
    Robolectric.getBackgroundScheduler().runOneTask();
    makeProviderReturn(secondCursor);
    assertThat(cursor.isClosed()).isTrue();
    assertThat(secondCursor.isClosed()).isFalse();
  }

  @Test
  public void shouldWrapCursorProperly() throws Exception {
    final Loader<MyCustomWrapper> loader = CursorLoaderBuilder.forUri(TEST_URI)
        .transform(genericToStringFunction)
        .wrap(new Function<List<String>, MyCustomWrapper>() {
          @Override
          public MyCustomWrapper apply(List<String> strings) {
            return new MyCustomWrapper(strings);
          }
        })
        .build(Robolectric.application);
    loader.startLoading();
    final Loader.OnLoadCompleteListener<MyCustomWrapper> listenerMock = mock(Loader.OnLoadCompleteListener.class);
    loader.registerListener(0, listenerMock);
    Robolectric.getBackgroundScheduler().runOneTask();
    verify(listenerMock).onLoadComplete(same(loader),
        eq(new MyCustomWrapper(Lists.newArrayList("my_name", "my_second_name"))));
  }

  @Test
  public void shouldNotCloseOldCursorInCaseItsSameAsNewOne() throws Exception {
    final Loader<MyCustomWrapper> loader = CursorLoaderBuilder.forUri(TEST_URI)
        .transform(genericToStringFunction)
        .wrap(new Function<List<String>, MyCustomWrapper>() {
          @Override
          public MyCustomWrapper apply(List<String> strings) {
            return new MyCustomWrapper(strings);
          }
        })
        .build(Robolectric.application);
    loader.startLoading();
    Robolectric.getBackgroundScheduler().runOneTask();
    loader.startLoading();
    Robolectric.getBackgroundScheduler().runOneTask();
    assertThat(cursor.isClosed()).isFalse();
  }

  @Test
  public void shouldCloseOldCursorDeliveredEarlierTwice() throws Exception {
    final Loader<MyCustomWrapper> loader = CursorLoaderBuilder.forUri(TEST_URI)
        .transform(genericToStringFunction)
        .wrap(new Function<List<String>, MyCustomWrapper>() {
          @Override
          public MyCustomWrapper apply(List<String> strings) {
            return new MyCustomWrapper(strings);
          }
        })
        .build(Robolectric.application);
    loader.startLoading();
    Robolectric.getBackgroundScheduler().runOneTask();
    loader.startLoading();
    Robolectric.getBackgroundScheduler().runOneTask();
    loader.reset();
    Robolectric.getBackgroundScheduler().runOneTask();

    assertThat(cursor.isClosed()).isTrue();
  }

  @Test
  public void shouldNotDeliverResultIfLoaderHasBeenResetAlready() throws Exception {
    final Loader<MyCustomWrapper> loader = CursorLoaderBuilder.forUri(TEST_URI)
        .transform(genericToStringFunction)
        .wrap(new Function<List<String>, MyCustomWrapper>() {
          @Override
          public MyCustomWrapper apply(List<String> strings) {
            return new MyCustomWrapper(strings);
          }
        })
        .build(Robolectric.application);

    final Loader.OnLoadCompleteListener<MyCustomWrapper> listenerMock = mock(Loader.OnLoadCompleteListener.class);
    loader.registerListener(0, listenerMock);
    loader.startLoading();
    loader.reset();
    Robolectric.getBackgroundScheduler().runTasks(1);
    makeProviderReturn(new MatrixCursor(cursor.getColumnNames()));
    loader.startLoading();
    Robolectric.getBackgroundScheduler().runTasks(1);
    verify(listenerMock, times(1)).onLoadComplete(same(loader), eq(new MyCustomWrapper(Lists.<String>newArrayList())));
  }

  private static class MyCustomWrapper {

    private List<String> strings;

    private MyCustomWrapper(List<String> strings) {
      this.strings = strings;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyCustomWrapper that = (MyCustomWrapper) o;
      return Objects.equal(strings, that.strings);
    }

    @Override
    public int hashCode() {
      return strings != null ? strings.hashCode() : 0;
    }
  }
}
