import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  buildFileTree,
  childFolderPath,
  collectFolderPaths,
  listDirectory,
  moveFileToFolder,
  moveFolderToFolder,
  removeFolderEntries,
  renameFilePath,
  renameFolderPath,
  selectedStats,
  uniqueFilePath
} from './fileTree.mjs'

const files = [
  { key: 'video', relativePath: 'Course/Chapter 1/video.mp4', size: 10 },
  { key: 'slides', relativePath: 'Course/Chapter 1/slides.pptx', size: 20 },
  { key: 'quiz', relativePath: 'Course/Chapter 2/quiz.pdf', size: 5 },
  { key: 'readme', relativePath: 'Course/readme.txt', size: 2 }
]

test('buildFileTree creates visual nested folders from relative paths', () => {
  const tree = buildFileTree(files)
  assert.equal(tree.fileCount, 4)
  assert.equal(tree.totalSize, 37)
  assert.deepEqual(
    collectFolderPaths(tree).map((item) => item.path),
    ['', 'Course', 'Course/Chapter 1', 'Course/Chapter 2']
  )
})

test('listDirectory returns folders and files for the current path', () => {
  const tree = buildFileTree(files)
  const root = listDirectory(tree, 'Course')
  assert.deepEqual(root.folders.map((item) => item.name), ['Chapter 1', 'Chapter 2'])
  assert.deepEqual(root.files.map((item) => item.name), ['readme.txt'])

  const chapter = listDirectory(tree, 'Course/Chapter 1')
  assert.deepEqual(chapter.folders, [])
  assert.deepEqual(chapter.files.map((item) => item.name), ['slides.pptx', 'video.mp4'])
})

test('selectedStats counts only checked files', () => {
  const stats = selectedStats(files, new Set(['video', 'quiz']))
  assert.equal(stats.count, 2)
  assert.equal(stats.size, 15)
})

test('buildFileTree keeps empty virtual folders visible', () => {
  const tree = buildFileTree(files, ['Course/Chapter 3'])
  assert.deepEqual(
    listDirectory(tree, 'Course').folders.map((item) => item.name),
    ['Chapter 1', 'Chapter 2', 'Chapter 3']
  )
  assert.equal(listDirectory(tree, 'Course/Chapter 3').folder.fileCount, 0)
})

test('childFolderPath validates and normalizes new folder names', () => {
  assert.equal(childFolderPath('', '  Chapter 4  '), 'Chapter 4')
  assert.equal(childFolderPath('Course', 'Chapter/4'), 'Course/Chapter4')
  assert.throws(() => childFolderPath('Course', '   '), /文件夹名称不能为空/)
})

test('moveFileToFolder rewrites only the selected file relative path', () => {
  const moved = moveFileToFolder(files, 'quiz', 'Course/Chapter 1')
  assert.equal(moved.find((item) => item.key === 'quiz').relativePath, 'Course/Chapter 1/quiz.pdf')
  assert.equal(moved.find((item) => item.key === 'video').relativePath, 'Course/Chapter 1/video.mp4')
})

test('uniqueFilePath appends a suffix when the target file already exists', () => {
  assert.equal(uniqueFilePath(files, 'Course/Chapter 1/video.mp4'), 'Course/Chapter 1/video (1).mp4')
  assert.equal(uniqueFilePath(files, 'Course/Chapter 1/notes'), 'Course/Chapter 1/notes')
})

test('moveFileToFolder avoids overwriting an existing file name', () => {
  const moved = moveFileToFolder(files, 'quiz', 'Course/Chapter 1')
  const movedAgain = moveFileToFolder(moved, 'readme', 'Course/Chapter 1')
  assert.equal(movedAgain.find((item) => item.key === 'readme').relativePath, 'Course/Chapter 1/readme.txt')

  const duplicate = moveFileToFolder([
    ...files,
    { key: 'otherQuiz', relativePath: 'Course/Chapter 1/quiz.pdf', size: 1 }
  ], 'quiz', 'Course/Chapter 1')
  assert.equal(duplicate.find((item) => item.key === 'quiz').relativePath, 'Course/Chapter 1/quiz (1).pdf')
  assert.equal(duplicate.find((item) => item.key === 'quiz').name, 'quiz (1).pdf')
})

test('renameFilePath keeps the file in the same folder', () => {
  const renamed = renameFilePath(files, 'quiz', 'exercise.pdf')
  assert.equal(renamed.find((item) => item.key === 'quiz').relativePath, 'Course/Chapter 2/exercise.pdf')
  assert.equal(renamed.find((item) => item.key === 'quiz').name, 'exercise.pdf')
})

test('renameFilePath rejects a duplicate file name in the same folder', () => {
  assert.throws(
    () => renameFilePath(files, 'slides', 'video.mp4'),
    /同目录已存在同名文件/
  )
})

test('renameFolderPath rewrites files and virtual folders below the folder', () => {
  const result = renameFolderPath(files, ['Course/Chapter 2/Empty'], 'Course/Chapter 2', 'Practice')
  assert.equal(result.files.find((item) => item.key === 'quiz').relativePath, 'Course/Practice/quiz.pdf')
  assert.deepEqual(result.virtualFolders, ['Course/Practice/Empty'])
  assert.equal(result.path, 'Course/Practice')
})

test('renameFolderPath rejects a duplicate sibling folder name', () => {
  assert.throws(
    () => renameFolderPath(files, [], 'Course/Chapter 2', 'Chapter 1'),
    /目标位置已存在同名文件夹/
  )
})

test('removeFolderEntries removes files and virtual folders below the folder', () => {
  const result = removeFolderEntries(files, ['Course/Chapter 2/Empty'], 'Course/Chapter 2')
  assert.equal(result.files.some((item) => item.key === 'quiz'), false)
  assert.deepEqual(result.virtualFolders, [])
  assert.equal(result.files.length, 3)
})

test('moveFolderToFolder moves a folder under another folder', () => {
  const result = moveFolderToFolder(files, ['Course/Chapter 2/Empty'], 'Course/Chapter 2', 'Course/Chapter 1')
  assert.equal(result.files.find((item) => item.key === 'quiz').relativePath, 'Course/Chapter 1/Chapter 2/quiz.pdf')
  assert.deepEqual(result.virtualFolders, ['Course/Chapter 1/Chapter 2/Empty'])
  assert.equal(result.path, 'Course/Chapter 1/Chapter 2')
})

test('moveFolderToFolder rejects a duplicate target folder name', () => {
  assert.throws(
    () => moveFolderToFolder(
      [...files, { key: 'archiveQuiz', relativePath: 'Archive/Chapter 2/quiz.pdf', size: 1 }],
      [],
      'Course/Chapter 2',
      'Archive'
    ),
    /目标位置已存在同名文件夹/
  )
})
